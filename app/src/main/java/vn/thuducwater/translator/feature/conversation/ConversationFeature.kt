package vn.thuducwater.translator.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import vn.thuducwater.translator.ui.components.PERM_MIC
import vn.thuducwater.translator.ui.components.rememberPermissionAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PAIRS = listOf("vi" to "en", "vi" to "ja", "vi" to "zh", "vi" to "ko")

data class ChatMessage(
    val speaker: String,
    val original: String,
    val translated: String,
    val src: String,
    val tgt: String,
    val time: Long = System.currentTimeMillis(),
)

data class ConversationUiState(
    val pair: Pair<String, String> = PAIRS.first(),
    val messages: List<ChatMessage> = emptyList(),
    /** "A" | "B" khi đang nghe */
    val activeSpeaker: String? = null,
    val speak: Boolean = true,
    val error: String? = null,
)

/**
 * Hội thoại 2 chiều: mỗi người bấm nút của mình rồi nói.
 * Bộ nhận dạng của Android cần biết trước ngôn ngữ, nên người nói được xác định bằng nút bấm.
 */
class ConversationViewModel(private val c: AppContainer) : ViewModel() {
    val listener = SpeechListener(c.appContext)
    private val _state = MutableStateFlow(ConversationUiState())
    val state = _state.asStateFlow()

    fun setPair(p: Pair<String, String>) = _state.update { it.copy(pair = p) }
    fun setSpeak(v: Boolean) = _state.update { it.copy(speak = v) }
    fun clear() = _state.update { it.copy(messages = emptyList()) }

    fun listen(speaker: String) {
        val (a, b) = _state.value.pair
        val src = if (speaker == "A") a else b
        val tgt = if (speaker == "A") b else a
        c.tts.stop()
        _state.update { it.copy(activeSpeaker = speaker, error = null) }
        listener.start(
            Languages.speechTag(src),
            onResult = { text -> onHeard(speaker, text, src, tgt) },
            onError = { msg -> _state.update { it.copy(activeSpeaker = null, error = msg) } },
        )
    }

    fun stop() = listener.stop()

    private fun onHeard(speaker: String, text: String, src: String, tgt: String) {
        _state.update { it.copy(activeSpeaker = null) }
        viewModelScope.launch {
            try {
                val out = c.translator.translate(text, src, tgt)
                c.history.add("conversation", text, out, src, tgt)
                _state.update { it.copy(messages = it.messages + ChatMessage(speaker, text, out, src, tgt)) }
                if (_state.value.speak) c.tts.speak(out, Languages.speechTag(tgt))
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyError("Không dịch được", e)) }
            }
        }
    }

    override fun onCleared() {
        listener.cancel()
    }
}

@Composable
fun ConversationScreen() {
    val vm = tdwViewModel { ConversationViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val listen by vm.listener.state.collectAsStateWithLifecycle()
    val withMic = rememberPermissionAction(PERM_MIC) { }
    val listState = rememberLazyListState()
    LaunchedEffect(s.messages.size) {
        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAIRS.forEach { p ->
                FilterChip(
                    selected = s.pair == p,
                    onClick = { vm.setPair(p) },
                    label = { Text("VI↔${p.second.uppercase()}") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.speak, onCheckedChange = vm::setSpeak)
            Text("Đọc bản dịch", modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clear) { Text("Xóa") }
        }
        ErrorText(s.error)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.messages) { m -> Bubble(m) }
        }
        if (s.activeSpeaker != null) {
            Text(
                "Người ${s.activeSpeaker} đang nói… ${listen.partial}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { listen.level }, modifier = Modifier.fillMaxWidth())
        }
        val (a, b) = s.pair
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeakerButton("A", a, s.activeSpeaker, Modifier.weight(1f), onStart = { withMic { vm.listen("A") } }, onStop = vm::stop)
            SpeakerButton("B", b, s.activeSpeaker, Modifier.weight(1f), onStart = { withMic { vm.listen("B") } }, onStop = vm::stop)
        }
    }
}

@Composable
private fun SpeakerButton(
    speaker: String,
    lang: String,
    active: String?,
    modifier: Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = active == speaker
    Button(
        onClick = { if (isActive) onStop() else onStart() },
        enabled = active == null || isActive,
        modifier = modifier.height(64.dp),
        colors = if (speaker == "B") {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null)
        Text(if (isActive) " Dừng" else " $speaker: ${Languages.displayName(lang)}")
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val isA = m.speaker == "A"
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isA) Alignment.CenterStart else Alignment.CenterEnd) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (isA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
        ) {
            Text("Người ${m.speaker} · ${fmt.format(Date(m.time))}", style = MaterialTheme.typography.labelSmall)
            Text(m.original, style = MaterialTheme.typography.bodyMedium)
            Text(m.translated, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
