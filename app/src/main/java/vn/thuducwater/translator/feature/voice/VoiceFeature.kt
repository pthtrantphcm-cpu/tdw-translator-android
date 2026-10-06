package vn.thuducwater.translator.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.data.Languages
import vn.thuducwater.translator.engine.SpeechListener
import vn.thuducwater.translator.feature.friendlyError
import vn.thuducwater.translator.feature.tdwViewModel
import vn.thuducwater.translator.ui.components.ErrorText
import vn.thuducwater.translator.ui.components.LanguageBar
import vn.thuducwater.translator.ui.components.PERM_MIC
import vn.thuducwater.translator.ui.components.rememberPermissionAction

data class VoiceUiState(
    val src: String = "vi",
    val tgt: String = "en",
    val transcript: String = "",
    val translation: String = "",
    val busy: Boolean = false,
    val speak: Boolean = true,
    val error: String? = null,
)

/** Micro → nhận dạng giọng nói (Android, offline) → ML Kit dịch → đọc kết quả. */
class VoiceViewModel(private val c: AppContainer) : ViewModel() {
    val listener = SpeechListener(c.appContext)
    private val _state = MutableStateFlow(VoiceUiState(speak = c.prefs.autoSpeak))
    val state = _state.asStateFlow()

    fun setSrc(v: String) = _state.update { it.copy(src = v) }
    fun setTgt(v: String) = _state.update { it.copy(tgt = v) }
    fun swap() = _state.update { it.copy(src = it.tgt, tgt = it.src) }
    fun setSpeak(v: Boolean) {
        c.prefs.autoSpeak = v
        _state.update { it.copy(speak = v) }
    }

    fun start() {
        if (!listener.isAvailable) {
            _state.update { it.copy(error = "Máy chưa có dịch vụ nhận dạng giọng nói (cần ứng dụng Google).") }
            return
        }
        c.tts.stop()
        _state.update { it.copy(error = null, transcript = "", translation = "") }
        listener.start(
            Languages.speechTag(_state.value.src),
            onResult = { text -> onTranscript(text) },
            onError = { msg -> _state.update { it.copy(error = msg) } },
        )
    }

    fun stop() = listener.stop()

    private fun onTranscript(text: String) {
        val s = _state.value
        _state.update { it.copy(transcript = text, busy = true) }
        viewModelScope.launch {
            try {
                val out = c.translator.translate(text, s.src, s.tgt)
                c.history.add("voice", text, out, s.src, s.tgt)
                _state.update { it.copy(translation = out, busy = false) }
                if (s.speak) replay()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = friendlyError("Không dịch được", e)) }
            }
        }
    }

    fun replay() {
        val s = _state.value
        c.tts.speak(s.translation, Languages.speechTag(s.tgt))?.let { err -> _state.update { it.copy(error = err) } }
    }

    override fun onCleared() {
        listener.cancel()
    }
}

@Composable
fun VoiceScreen() {
    val vm = tdwViewModel { VoiceViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val listen by vm.listener.state.collectAsStateWithLifecycle()
    val withMic = rememberPermissionAction(PERM_MIC) { }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguageBar(s.src, s.tgt, vm::setSrc, vm::setTgt, vm::swap, allowAuto = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { withMic { vm.start() } }, enabled = !listen.listening, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Text(" Start")
            }
            OutlinedButton(onClick = vm::stop, enabled = listen.listening, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Text(" Stop")
            }
            OutlinedButton(onClick = vm::replay, enabled = s.translation.isNotBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Text(" Replay")
            }
        }
        if (listen.listening) {
            Text("🔴 Đang nghe… nói xong bấm Stop hoặc ngừng nói", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { listen.level }, modifier = Modifier.fillMaxWidth())
            if (listen.partial.isNotBlank()) Text(listen.partial, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.speak, onCheckedChange = vm::setSpeak)
            Text("Tự đọc bản dịch")
        }
        ErrorText(s.error)
        if (s.transcript.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bạn nói (${Languages.displayName(s.src)})", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(s.transcript, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
        if (s.translation.isNotBlank() || s.busy) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bản dịch (${Languages.displayName(s.tgt)})", style = MaterialTheme.typography.labelMedium)
                    if (s.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    SelectionContainer { Text(s.translation, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
