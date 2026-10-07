package vn.thuducwater.translator.feature.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.data.AUTO
import vn.thuducwater.translator.engine.OcrLine
import vn.thuducwater.translator.engine.renderOverlay
import vn.thuducwater.translator.feature.friendlyError
import vn.thuducwater.translator.feature.tdwViewModel
import vn.thuducwater.translator.ui.components.ErrorText
import vn.thuducwater.translator.ui.components.LanguageBar
import vn.thuducwater.translator.ui.components.PERM_CAMERA
import vn.thuducwater.translator.ui.components.rememberPermissionAction
import java.io.File

data class OcrUiState(
    val src: String = AUTO,
    val tgt: String = "vi",
    val original: Bitmap? = null,
    val overlay: Bitmap? = null,
    val lines: List<OcrLine> = emptyList(),
    val showOverlay: Boolean = true,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

/** Đọc ảnh từ Uri, tự xoay theo EXIF, thu nhỏ cạnh dài tối đa [maxSide] px. */
fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap {
    val cr = context.contentResolver
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = maxSide.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Không đọc được ảnh")
    }
}

class OcrViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OcrUiState(tgt = c.prefs.targetLang))
    val state = _state.asStateFlow()

    fun setSrc(v: String) {
        _state.update { it.copy(src = v) }
        rerun()
    }

    fun setTgt(v: String) {
        _state.update { it.copy(tgt = v) }
        rerun()
    }

    fun swap() = _state.update { it.copy(src = if (it.tgt == AUTO) "en" else it.tgt, tgt = if (it.src == AUTO) "en" else it.src) }
    fun toggleOverlay(v: Boolean) = _state.update { it.copy(showOverlay = v) }

    fun openUri(context: Context, uri: Uri) {
        _state.update { it.copy(busy = true, error = null, status = "Đang mở ảnh…") }
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
                _state.update { it.copy(original = bmp, overlay = null, lines = emptyList()) }
                process(bmp)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, status = null, error = friendlyError("Không mở được ảnh", e)) }
            }
        }
    }

    private fun rerun() {
        _state.value.original?.let { bmp -> viewModelScope.launch { process(bmp) } }
    }

    private suspend fun process(bmp: Bitmap) {
        val s = _state.value
        _state.update { it.copy(busy = true, error = null, status = "Đang nhận dạng chữ…") }
        try {
            val lines = c.ocr.recognize(InputImage.fromBitmap(bmp, 0), if (s.src == AUTO) null else s.src)
            if (lines.isEmpty()) {
                _state.update { it.copy(busy = false, status = null, lines = emptyList(), overlay = null, error = "Không tìm thấy chữ trong ảnh.") }
                return
            }
            val joined = lines.joinToString("\n") { it.text }
            val src = if (s.src == AUTO) c.translator.detect(joined) ?: "en" else s.src
            _state.update { it.copy(status = "Đang dịch ${lines.size} dòng…") }
            val translated = lines.map { line ->
                line.copy(translated = c.translator.translate(line.text, src, s.tgt) { msg ->
                    _state.update { it.copy(status = msg) }
                })
            }
            val overlay = withContext(Dispatchers.Default) { renderOverlay(bmp, translated) }
            c.history.add("ocr", joined, translated.joinToString("\n") { it.translated }, src, s.tgt)
            _state.update { it.copy(lines = translated, overlay = overlay, busy = false, status = null) }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, status = null, error = friendlyError("Lỗi OCR/dịch", e)) }
        }
    }
}

@Composable
fun OcrScreen() {
    val vm = tdwViewModel { OcrViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.openUri(context, uri)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.openUri(context, uri)
    }
    val withCamera = rememberPermissionAction(PERM_CAMERA) { }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguageBar(s.src, s.tgt, vm::setSrc, vm::setTgt, vm::swap, allowAuto = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Text(" Chọn ảnh")
            }
            OutlinedButton(
                onClick = {
                    withCamera {
                        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        photoUri = uri
                        takePicture.launch(uri)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text(" Chụp ảnh")
            }
        }
        if (s.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(s.status.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
        ErrorText(s.error)
        val shown = if (s.showOverlay) s.overlay ?: s.original else s.original
        if (shown != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.showOverlay, onClick = { vm.toggleOverlay(true) }, label = { Text("Bản dịch") })
                FilterChip(selected = !s.showOverlay, onClick = { vm.toggleOverlay(false) }, label = { Text("Ảnh gốc") })
            }
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = "Ảnh",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (s.lines.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Song ngữ (${s.lines.size} dòng)", style = MaterialTheme.typography.titleSmall)
                    s.lines.forEach { line ->
                        Text(line.translated, style = MaterialTheme.typography.bodyLarge)
                        Text(line.text, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
