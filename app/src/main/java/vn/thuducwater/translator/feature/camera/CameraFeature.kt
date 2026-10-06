package vn.thuducwater.translator.feature.camera

import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.data.AUTO
import vn.thuducwater.translator.engine.OcrLine
import vn.thuducwater.translator.feature.friendlyError
import vn.thuducwater.translator.feature.tdwViewModel
import vn.thuducwater.translator.ui.components.ErrorText
import vn.thuducwater.translator.ui.components.LanguageBar
import vn.thuducwater.translator.ui.components.PERM_CAMERA
import vn.thuducwater.translator.ui.components.hasPermission
import vn.thuducwater.translator.ui.components.rememberPermissionAction
import java.util.concurrent.Executors

data class CameraUiState(
    val src: String = AUTO,
    val tgt: String = "vi",
    val running: Boolean = false,
    val frozen: Boolean = false,
    val lines: List<OcrLine> = emptyList(),
    /** Kích thước ảnh phân tích (đã xoay đúng chiều) để quy đổi tọa độ sang màn hình */
    val imageWidth: Int = 1,
    val imageHeight: Int = 1,
    val error: String? = null,
)

/** Webcam realtime: OCR định kỳ trên khung hình mới nhất, dịch và vẽ đè lên preview. */
class CameraViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CameraUiState(tgt = c.prefs.targetLang))
    val state = _state.asStateFlow()
    private val cache = HashMap<String, String>()
    private var lastRun = 0L

    @Volatile
    private var busy = false
    val analysisExecutor = Executors.newSingleThreadExecutor()

    fun setSrc(v: String) {
        cache.clear()
        _state.update { it.copy(src = v, lines = emptyList()) }
    }

    fun setTgt(v: String) {
        cache.clear()
        _state.update { it.copy(tgt = v, lines = emptyList()) }
    }

    fun swap() {
        cache.clear()
        _state.update { it.copy(src = it.tgt, tgt = if (it.src == AUTO) "en" else it.src, lines = emptyList()) }
    }
    fun setRunning(v: Boolean) = _state.update { it.copy(running = v, lines = if (v) it.lines else emptyList()) }
    fun setFrozen(v: Boolean) = _state.update { it.copy(frozen = v) }

    /** Gọi trên luồng phân tích của CameraX. Luôn đóng [proxy] khi xong. */
    @OptIn(ExperimentalGetImage::class)
    fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = proxy.image
        val s = _state.value
        if (media == null || busy || s.frozen || now - lastRun < INTERVAL_MS) {
            proxy.close()
            return
        }
        busy = true
        lastRun = now
        val rotation = proxy.imageInfo.rotationDegrees
        val w = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
        val h = if (rotation == 90 || rotation == 270) proxy.width else proxy.height
        val image = InputImage.fromMediaImage(media, rotation)
        viewModelScope.launch {
            try {
                val lines = c.ocr.recognize(image, if (s.src == AUTO) null else s.src)
                proxy.close()
                val src = if (s.src == AUTO) {
                    c.translator.detect(lines.joinToString(" ") { it.text }) ?: "en"
                } else {
                    s.src
                }
                val translated = lines.map { line ->
                    val t = cache.getOrPut("$src|${line.text}") { c.translator.translate(line.text, src, s.tgt) }
                    line.copy(translated = t)
                }
                if (cache.size > 500) cache.clear()
                _state.update { it.copy(lines = translated, imageWidth = w, imageHeight = h, error = null) }
            } catch (e: Exception) {
                proxy.close()
                _state.update { it.copy(error = friendlyError("Lỗi OCR", e)) }
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        analysisExecutor.shutdown()
    }

    companion object {
        const val INTERVAL_MS = 1200L
    }
}

@Composable
fun CameraScreen() {
    val vm = tdwViewModel { CameraViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val withCamera = rememberPermissionAction(PERM_CAMERA) { }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageBar(s.src, s.tgt, vm::setSrc, vm::setTgt, vm::swap, allowAuto = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (s.running) vm.setRunning(false) else withCamera { vm.setRunning(true) }
            }) { Text(if (s.running) "Tắt camera" else "Bật camera") }
            FilterChip(selected = s.frozen, onClick = { vm.setFrozen(!s.frozen) }, label = { Text("Đóng băng") })
            Text("${s.lines.size} dòng", style = MaterialTheme.typography.bodySmall)
        }
        ErrorText(s.error)
        if (s.running && hasPermission(context, PERM_CAMERA)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CameraPreview(vm)
                TranslationOverlay(s)
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Bấm “Bật camera” rồi hướng camera vào biển báo, thực đơn, tài liệu…")
            }
        }
    }
}

@Composable
private fun CameraPreview(vm: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(vm.analysisExecutor) { proxy -> vm.analyze(proxy) } }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) future.get().unbindAll()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Quy đổi tọa độ ảnh phân tích sang khung preview (FILL_CENTER) rồi vẽ bản dịch. */
@Composable
private fun TranslationOverlay(s: CameraUiState) {
    Canvas(Modifier.fillMaxSize()) {
        val scale = maxOf(size.width / s.imageWidth, size.height / s.imageHeight)
        val dx = (size.width - s.imageWidth * scale) / 2f
        val dy = (size.height - s.imageHeight * scale) / 2f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val bgPaint = Paint().apply { color = Color(0xD9000000).toArgb() }
        for (line in s.lines) {
            if (line.translated.isBlank()) continue
            val r: Rect = line.box
            val left = r.left * scale + dx
            val top = r.top * scale + dy
            val height = (r.height() * scale).coerceAtLeast(16f)
            textPaint.textSize = height * 0.75f
            val width = maxOf(r.width() * scale, textPaint.measureText(line.translated) + 8f)
            drawContext.canvas.nativeCanvas.apply {
                drawRect(left, top, left + width, top + height, bgPaint)
                drawText(line.translated, left + 4f, top + height * 0.8f, textPaint)
            }
        }
    }
}
