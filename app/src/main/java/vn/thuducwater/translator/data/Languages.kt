package vn.thuducwater.translator.data

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** Mã ngôn ngữ đặc biệt: tự nhận diện. */
const val AUTO = "auto"

data class Lang(val code: String, val name: String)

private val VI = Locale.forLanguageTag("vi")

/** Mã BCP-47 cho nhận dạng giọng nói / đọc văn bản. */
private val SPEECH_TAGS = mapOf(
    TranslateLanguage.VIETNAMESE to "vi-VN",
    TranslateLanguage.ENGLISH to "en-US",
    TranslateLanguage.JAPANESE to "ja-JP",
    TranslateLanguage.CHINESE to "zh-CN",
    TranslateLanguage.KOREAN to "ko-KR",
    TranslateLanguage.FRENCH to "fr-FR",
    TranslateLanguage.GERMAN to "de-DE",
    TranslateLanguage.SPANISH to "es-ES",
    TranslateLanguage.THAI to "th-TH",
    TranslateLanguage.RUSSIAN to "ru-RU",
)

private val PRIORITY = listOf(
    TranslateLanguage.VIETNAMESE,
    TranslateLanguage.ENGLISH,
    TranslateLanguage.JAPANESE,
    TranslateLanguage.CHINESE,
    TranslateLanguage.KOREAN,
)

object Languages {
    /** 59 ngôn ngữ ML Kit hỗ trợ, tên hiển thị bằng tiếng Việt, ưu tiên Việt/Anh/Nhật/Trung/Hàn. */
    val all: List<Lang> by lazy {
        val langs = TranslateLanguage.getAllLanguages().map { Lang(it, displayName(it)) }
        val head = PRIORITY.mapNotNull { p -> langs.firstOrNull { it.code == p } }
        head + langs.filter { it.code !in PRIORITY }.sortedBy { it.name.lowercase(VI) }
    }

    fun displayName(code: String): String {
        if (code == AUTO) return "Tự nhận diện"
        val name = Locale.forLanguageTag(code).getDisplayName(VI)
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(VI) else it.toString() }
    }

    fun speechTag(code: String): String = SPEECH_TAGS[code] ?: code
}
