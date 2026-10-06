package vn.thuducwater.translator.feature.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.data.AUTO
import vn.thuducwater.translator.data.Languages
import vn.thuducwater.translator.feature.friendlyError
import vn.thuducwater.translator.feature.tdwViewModel
import vn.thuducwater.translator.ui.components.ErrorText
import vn.thuducwater.translator.ui.components.LanguageBar

data class TextUiState(
    val input: String = "",
    val output: String = "",
    val src: String = AUTO,
    val tgt: String = "vi",
    val detected: String? = null,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val historyId: Long? = null,
    val favorite: Boolean = false,
    val elapsedMs: Long = 0,
)

class TextViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TextUiState(tgt = c.prefs.targetLang))
    val state = _state.asStateFlow()

    fun onInput(v: String) = _state.update { it.copy(input = v) }
    fun setSrc(v: String) = _state.update { it.copy(src = v) }
    fun setTgt(v: String) {
        c.prefs.targetLang = v
        _state.update { it.copy(tgt = v) }
    }

    fun swap() = _state.update {
        val newTgt = if (it.src == AUTO) (it.detected ?: "en") else it.src
        it.copy(src = it.tgt, tgt = newTgt, input = it.output.ifBlank { it.input }, output = "")
    }

    fun clear() = _state.update { it.copy(input = "", output = "", detected = null, historyId = null, error = null) }

    fun translate() {
        val s = _state.value
        if (s.input.isBlank() || s.busy) return
        _state.update { it.copy(busy = true, error = null, status = "Đang dịch…") }
        viewModelScope.launch {
            try {
                val t0 = System.currentTimeMillis()
                val src = if (s.src == AUTO) c.translator.detect(s.input) ?: "en" else s.src
                val out = c.translator.translate(s.input, src, s.tgt) {
                    _state.update { it.copy(status = "Đang tải gói ngôn ngữ lần đầu (~30 MB mỗi ngôn ngữ)…") }
                }
                val id = c.history.add("text", s.input, out, src, s.tgt)
                _state.update {
                    it.copy(
                        output = out, busy = false, status = null, historyId = id.takeIf { v -> v > 0 },
                        favorite = false, detected = if (s.src == AUTO) src else null,
                        elapsedMs = System.currentTimeMillis() - t0,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, status = null, error = friendlyError("Không dịch được", e)) }
            }
        }
    }

    fun toggleFavorite() {
        val id = _state.value.historyId ?: return
        viewModelScope.launch {
            val fav = c.history.toggleFavorite(id)
            _state.update { it.copy(favorite = fav) }
        }
    }

    fun speak() {
        val s = _state.value
        c.tts.speak(s.output, Languages.speechTag(s.tgt))?.let { err -> _state.update { it.copy(error = err) } }
    }
}

@Composable
fun TextScreen() {
    val vm = tdwViewModel { TextViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguageBar(s.src, s.tgt, vm::setSrc, vm::setTgt, vm::swap, allowAuto = true)
        OutlinedTextField(
            value = s.input,
            onValueChange = vm::onInput,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            placeholder = { Text("Nhập hoặc dán văn bản cần dịch") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.input.length} ký tự", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.getText()?.let { vm.onInput(it.text) } }) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Dán")
            }
            TextButton(onClick = vm::clear) { Text("Xóa") }
            Button(onClick = vm::translate, enabled = !s.busy && s.input.isNotBlank()) { Text("Dịch") }
        }
        if (s.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(s.status.orEmpty())
            }
        }
        ErrorText(s.error)
        if (s.output.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.detected?.let { Text("Phát hiện: ${Languages.displayName(it)}", style = MaterialTheme.typography.labelMedium) }
                    SelectionContainer { Text(s.output, style = MaterialTheme.typography.bodyLarge) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.elapsedMs} ms", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = vm::speak) { Icon(Icons.Filled.VolumeUp, contentDescription = "Đọc") }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(s.output)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép")
                        }
                        IconButton(onClick = vm::toggleFavorite, enabled = s.historyId != null) {
                            Icon(if (s.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Yêu thích")
                        }
                    }
                }
            }
        }
    }
}
