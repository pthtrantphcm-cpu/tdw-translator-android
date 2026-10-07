package vn.thuducwater.translator.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ListenState(
    val listening: Boolean = false,
    val partial: String = "",
    val level: Float = 0f,
)

/**
 * Nhận dạng giọng nói bằng bộ nhận dạng của Android.
 * Android 12+ có bộ nhận dạng chạy hẳn trên máy (on-device); máy cũ hơn dùng chế độ ưu tiên offline.
 * Phải gọi trên luồng chính (main thread).
 */
class SpeechListener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(speechTag: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = ListenState(listening = true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(level = 0f)
            }

            override fun onError(error: Int) {
                _state.value = ListenState()
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = ListenState()
                if (text.isNotBlank()) onResult(text) else onError("Không nghe rõ, hãy nói lại.")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = _state.value.copy(partial = text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        rec.startListening(intent)
    }

    /** Dừng nghe và trả kết quả đã nghe được. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _state.value = ListenState()
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe rõ, hãy nói lại gần micro hơn."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "Chưa có gói nhận dạng giọng nói offline cho ngôn ngữ này. Vào Cài đặt app để tải gói."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Ứng dụng chưa được cấp quyền micro."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận dạng đang bận, thử lại sau giây lát."
        SpeechRecognizer.ERROR_AUDIO -> "Lỗi thu âm từ micro."
        12, 13 -> "Ngôn ngữ này chưa có gói nhận dạng trên máy. Vào Cài đặt app để tải gói."
        else -> "Lỗi nhận dạng giọng nói (mã $code)."
    }
}

/** Đọc văn bản bằng giọng có sẵn của Android (Google TTS hỗ trợ tiếng Việt, offline nếu đã tải giọng). */
class TtsEngine(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    var ready = false
        private set

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    /** Trả về null nếu đọc được, hoặc thông báo lỗi tiếng Việt. */
    fun speak(text: String, speechTag: String): String? {
        if (text.isBlank()) return null
        if (!ready) return "Bộ đọc văn bản chưa sẵn sàng."
        val r = tts.setLanguage(Locale.forLanguageTag(speechTag))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            return "Máy chưa có giọng đọc cho ngôn ngữ này (Cài đặt > Đọc văn bản)."
        }
        pickVoice(Locale.forLanguageTag(speechTag))
        tts.setSpeechRate(0.95f) // chậm hơn một chút cho rõ
        tts.setPitch(1.0f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tdw-${System.nanoTime()}")
        return null
    }

    /**
     * Chọn giọng tốt nhất cho ngôn ngữ: ưu tiên giọng đã tải về máy (không cần mạng, không bị ngắt),
     * chất lượng cao, tên có dấu hiệu giọng nam. Không chọn được thì giữ giọng mặc định.
     */
    private fun pickVoice(locale: Locale) {
        val voices = try { tts.voices } catch (e: Exception) { null } ?: return
        val best = voices
            .filter { it.locale.language == locale.language }
            .filter { "notInstalled" !in it.features }
            .maxByOrNull { v ->
                val name = v.name.lowercase(Locale.ROOT)
                var score = v.quality
                if (!v.isNetworkConnectionRequired) score += 1000
                if (v.locale.country == locale.country) score += 200
                if ("male" in name && "female" !in name) score += 100
                score
            } ?: return
        if (tts.voice?.name != best.name) tts.voice = best
    }

    fun stop() {
        tts.stop()
    }
}
