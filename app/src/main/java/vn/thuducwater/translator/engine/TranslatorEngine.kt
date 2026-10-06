package vn.thuducwater.translator.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Dịch offline bằng Google ML Kit Translation.
 * Mỗi ngôn ngữ cần một gói ~30 MB, tải 1 lần (cần mạng), sau đó dịch không cần Internet.
 */
class TranslatorEngine {
    private val clients = HashMap<Pair<String, String>, Translator>()
    private val modelManager = RemoteModelManager.getInstance()
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build(),
    )

    /** Trả về mã ngôn ngữ ML Kit, hoặc null nếu không chắc chắn. */
    suspend fun detect(text: String): String? {
        if (text.isBlank()) return null
        val tag = languageId.identifyLanguage(text).await()
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
        modelManager.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()

    /**
     * Dịch văn bản. [onDownloading] được gọi khi phải tải gói ngôn ngữ lần đầu
     * để giao diện báo cho người dùng biết vì sao chậm.
     */
    suspend fun translate(text: String, src: String, tgt: String, onDownloading: () -> Unit = {}): String {
        if (text.isBlank() || src == tgt) return text
        if (!isDownloaded(src) || !isDownloaded(tgt)) onDownloading()
        val translator = client(src, tgt)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return translator.translate(text).await()
    }

    suspend fun downloadedLanguages(): Set<String> =
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()

    suspend fun download(lang: String) {
        modelManager.download(TranslateRemoteModel.Builder(lang).build(), DownloadConditions.Builder().build()).await()
    }

    suspend fun delete(lang: String) {
        modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build()).await()
    }
}
