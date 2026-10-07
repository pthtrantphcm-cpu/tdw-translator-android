package vn.thuducwater.translator.engine

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import vn.thuducwater.translator.data.Languages

/** Lỗi đã có sẵn câu thông báo tiếng Việt cho người dùng. */
class TdwException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Dịch offline bằng Google ML Kit Translation.
 * Mỗi ngôn ngữ cần một gói ~30 MB, tải 1 lần (cần mạng), sau đó dịch không cần Internet.
 *
 * ML Kit tải gói qua DownloadManager của Android và không báo tiến độ. Nếu DownloadManager bị
 * hệ điều hành chặn/treo (hay gặp trên ColorOS, MIUI khi bật tiết kiệm pin/dữ liệu) thì Task của
 * ML Kit không bao giờ kết thúc. Vì vậy mọi lời gọi ở đây đều có giới hạn thời gian, và trong lúc
 * tải sẽ đọc trạng thái DownloadManager để báo số MB đã tải hoặc lý do bị dừng.
 */
class TranslatorEngine(context: Context) {
    private val appContext = context.applicationContext
    private val clients = HashMap<Pair<String, String>, Translator>()
    private val modelManager = RemoteModelManager.getInstance()
    private val downloadLock = Mutex()
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build(),
    )

    /** Trả về mã ngôn ngữ ML Kit, hoặc null nếu không chắc chắn / quá thời gian. */
    suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        val tag = withTimeoutOrNull(DETECT_TIMEOUT_MS) { languageId.identifyLanguage(text).await() } ?: return null
        if (tag == "und") return null
        return TranslateLanguage.fromLanguageTag(tag.substringBefore('-'))
    }

    private fun client(src: String, tgt: String): Translator = synchronized(clients) {
        clients.getOrPut(src to tgt) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build(),
            )
        }
    }

    suspend fun isDownloaded(lang: String): Boolean =
        withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            modelManager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()
        } ?: false

    /**
     * Dịch văn bản. [onStatus] nhận câu trạng thái tiếng Việt khi phải tải gói ngôn ngữ lần đầu
     * (kèm số MB đã tải) để giao diện không quay vòng mà không rõ lý do.
     */
    suspend fun translate(
        text: String,
        src: String,
        tgt: String,
        onStatus: (String) -> Unit = {},
    ): String {
        if (text.isBlank() || src == tgt) return text
        ensureModels(listOf(src, tgt), onStatus)
        val translator = client(src, tgt)
        return try {
            withTimeout(TRANSLATE_TIMEOUT_MS) {
                // Gói đã có trên máy nên bước này chỉ nạp model, không tải mạng
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                translator.translate(text).await()
            }
        } catch (e: TimeoutCancellationException) {
            throw TdwException("Bộ dịch không phản hồi sau ${TRANSLATE_TIMEOUT_MS / 1000} giây. Thoát hẳn app rồi mở lại.", e)
        }
    }

    /** Đảm bảo gói của các ngôn ngữ đã nằm trên máy, chưa có thì tải (có giám sát). */
    suspend fun ensureModels(langs: Collection<String>, onStatus: (String) -> Unit = {}) {
        for (lang in langs.distinct()) {
            if (lang == TranslateLanguage.ENGLISH) continue // tiếng Anh có sẵn trong ML Kit
            if (!isDownloaded(lang)) downloadWatched(lang, onStatus)
        }
    }

    suspend fun downloadedLanguages(): Set<String> =
        withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
        } ?: emptySet()

    suspend fun download(lang: String, onStatus: (String) -> Unit = {}) = downloadWatched(lang, onStatus)

    suspend fun delete(lang: String) {
        withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build()).await()
        }
        synchronized(clients) {
            clients.keys.filter { it.first == lang || it.second == lang }.forEach { key -> clients.remove(key)?.close() }
        }
    }

    /**
     * Tải một gói và theo dõi DownloadManager mỗi giây.
     * Báo lỗi rõ ràng khi: không có mạng, DownloadManager báo FAILED, không nhận thêm byte nào
     * trong [STALL_TIMEOUT_MS], hoặc quá [DOWNLOAD_TIMEOUT_MS].
     */
    private suspend fun downloadWatched(lang: String, onStatus: (String) -> Unit) = downloadLock.withLock {
        if (isDownloaded(lang)) return@withLock
        val name = Languages.displayName(lang)
        if (!hasNetwork()) {
            throw TdwException("Máy chưa có gói $name và đang không có mạng. Bật Wi-Fi/4G để tải gói một lần (~30 MB).")
        }
        onStatus("Đang tải gói $name (~30 MB), chỉ tải lần đầu…")
        // Bỏ qua các lượt tải cũ đã xong/hỏng; lượt đang treo từ lần trước vẫn được theo dõi
        val oldFinished = queryDownloads().filter { it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED }.map { it.id }.toSet()
        val startedAt = System.currentTimeMillis()
        var lastBytes = -1L
        var lastProgressAt = startedAt
        var sawInfo = false
        try {
            coroutineScope {
                val job = async {
                    modelManager.download(
                        TranslateRemoteModel.Builder(lang).build(),
                        DownloadConditions.Builder().build(),
                    ).await()
                }
                while (withTimeoutOrNull(POLL_MS) { job.join(); true } != true) {
                    val now = System.currentTimeMillis()
                    val info = queryDownloads().filter { it.id !in oldFinished }.maxByOrNull { it.id }
                    if (info != null) {
                        sawInfo = true
                        if (info.bytes != lastBytes) {
                            lastBytes = info.bytes
                            lastProgressAt = now
                        }
                        when (info.status) {
                            DownloadManager.STATUS_FAILED -> {
                                job.cancel()
                                throw TdwException("Tải gói $name thất bại (mã ${info.reason}). Kiểm tra mạng rồi thử lại.")
                            }
                            DownloadManager.STATUS_PAUSED -> onStatus("Tải gói $name đang bị tạm dừng: ${pausedReason(info.reason)}")
                            else -> onStatus("Đang tải gói $name: ${progressText(info)}")
                        }
                    }
                    // Không đọc được DownloadManager thì không biết tiến độ, nên cho chờ lâu hơn
                    val stallLimit = if (sawInfo) STALL_TIMEOUT_MS else NO_INFO_TIMEOUT_MS
                    if (now - lastProgressAt > stallLimit) {
                        job.cancel()
                        throw TdwException(
                            "Tải gói $name bị treo (${stallLimit / 1000} giây không nhận thêm dữ liệu). " +
                                "Trên Oppo: Cài đặt > Ứng dụng > TDW Translator > Sử dụng pin > cho phép chạy nền, " +
                                "tắt Tiết kiệm dữ liệu, và kiểm tra ứng dụng hệ thống \"Trình quản lý tải xuống\" không bị tắt. Rồi thử lại.",
                        )
                    }
                    if (now - startedAt > DOWNLOAD_TIMEOUT_MS) {
                        job.cancel()
                        throw TdwException("Tải gói $name quá ${DOWNLOAD_TIMEOUT_MS / 60000} phút. Đổi sang Wi-Fi khác rồi thử lại.")
                    }
                }
                job.await()
            }
        } catch (e: TdwException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // người dùng rời màn hình: không đổi thành lỗi
        } catch (e: Exception) {
            throw TdwException("Không tải được gói $name: ${e.message.orEmpty().take(150)}", e)
        }
        onStatus("Đã tải xong gói $name.")
    }

    private data class DlInfo(val id: Long, val status: Int, val reason: Int, val bytes: Long, val total: Long)

    /** Các lượt tải của chính app này trong DownloadManager (ML Kit tải dưới UID của app). */
    private fun queryDownloads(): List<DlInfo> = runCatching {
        val dm = appContext.getSystemService(DownloadManager::class.java) ?: return emptyList()
        dm.query(DownloadManager.Query()).use { c ->
            val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val stCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val rsCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            val byCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val toCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val out = ArrayList<DlInfo>()
            while (c.moveToNext()) {
                out += DlInfo(c.getLong(idCol), c.getInt(stCol), c.getInt(rsCol), c.getLong(byCol), c.getLong(toCol))
            }
            out
        }
    }.getOrDefault(emptyList())

    private fun progressText(i: DlInfo): String {
        val mb = i.bytes.coerceAtLeast(0) / 1_048_576.0
        return if (i.total > 0) {
            "%.1f / %.1f MB".format(mb, i.total / 1_048_576.0)
        } else if (i.status == DownloadManager.STATUS_PENDING) {
            "đang chờ hệ thống bắt đầu tải…"
        } else {
            "%.1f MB".format(mb)
        }
    }

    private fun pausedReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "đang chờ có mạng."
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "hệ thống chỉ cho tải qua Wi-Fi, hãy bật Wi-Fi."
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "lỗi mạng, hệ thống đang thử lại."
        else -> "lý do $reason."
    }

    private fun hasNetwork(): Boolean = runCatching {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    companion object {
        private const val POLL_MS = 1_000L
        private const val CHECK_TIMEOUT_MS = 10_000L
        private const val DETECT_TIMEOUT_MS = 10_000L
        private const val TRANSLATE_TIMEOUT_MS = 30_000L
        private const val STALL_TIMEOUT_MS = 90_000L
        private const val NO_INFO_TIMEOUT_MS = 180_000L
        private const val DOWNLOAD_TIMEOUT_MS = 10 * 60_000L
    }
}
