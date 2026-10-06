package vn.thuducwater.translator.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrLine(val text: String, val box: Rect, val translated: String = "")

/** OCR on-device bằng ML Kit Text Recognition v2 (Latin gồm cả tiếng Việt, Trung, Nhật, Hàn). */
class OcrEngine {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val chinese by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val japanese by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val korean by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    private fun recognizerFor(lang: String?): TextRecognizer = when (lang) {
        TranslateLanguage.CHINESE -> chinese
        TranslateLanguage.JAPANESE -> japanese
        TranslateLanguage.KOREAN -> korean
        else -> latin
    }

    /** [lang] = ngôn ngữ chữ trong ảnh, null = tự đoán (thử Latin, không ra chữ thì thử bộ Nhật/Hán). */
    suspend fun recognize(image: InputImage, lang: String?): List<OcrLine> {
        val first = run(recognizerFor(lang), image)
        if (first.isNotEmpty() || lang != null) return first
        return run(japanese, image)
    }

    private suspend fun run(recognizer: TextRecognizer, image: InputImage): List<OcrLine> {
        val result = recognizer.process(image).await()
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, it) } }
        }
    }
}

/** Vẽ bản dịch đè lên vị trí chữ gốc (nền lấy theo màu viền vùng chữ). */
fun renderOverlay(source: Bitmap, lines: List<OcrLine>): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    for (line in lines) {
        if (line.translated.isBlank()) continue
        val text = line.translated
        val r = Rect(line.box).apply { intersect(0, 0, out.width, out.height) }
        if (r.width() < 4 || r.height() < 4) continue
        val bg = source.getPixel(r.left.coerceIn(0, source.width - 1), r.top.coerceIn(0, source.height - 1))
        val lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)
        fill.color = bg
        canvas.drawRect(r, fill)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lum > 140) Color.BLACK else Color.WHITE }
        // Giảm cỡ chữ đến khi vừa khung
        var size = r.height() * 0.8f
        var layout: StaticLayout
        do {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, r.width())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            size -= 1f
        } while (layout.height > r.height() * 1.1f && size > 8f)
        canvas.save()
        canvas.translate(r.left.toFloat(), r.top + ((r.height() - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(canvas)
        canvas.restore()
    }
    return out
}
