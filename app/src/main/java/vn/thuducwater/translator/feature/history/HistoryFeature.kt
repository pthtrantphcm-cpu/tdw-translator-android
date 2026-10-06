package vn.thuducwater.translator.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.data.HistoryEntity
import vn.thuducwater.translator.data.Languages
import vn.thuducwater.translator.feature.tdwViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KIND_LABEL = mapOf(
    "text" to "Văn bản",
    "voice" to "Giọng nói",
    "conversation" to "Hội thoại",
    "ocr" to "Ảnh",
    "camera" to "Camera",
)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val all = c.history.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = c.history.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { c.history.toggleFavorite(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { c.history.delete(id) }
    }

    fun clear() {
        viewModelScope.launch { c.history.clear() }
    }
}

@Composable
fun HistoryScreen() {
    val vm = tdwViewModel { HistoryViewModel(it) }
    val all by vm.all.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    var onlyFav by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = if (onlyFav) favs else all

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyFav, onClick = { onlyFav = false }, label = { Text("Tất cả (${all.size})") })
            FilterChip(selected = onlyFav, onClick = { onlyFav = true }, label = { Text("Yêu thích (${favs.size})") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }, enabled = all.isNotEmpty()) { Text("Xóa hết") }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Chưa có bản dịch nào") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { item ->
                    HistoryCard(item, vm::toggleFavorite, vm::delete)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa toàn bộ lịch sử?") },
            text = { Text("Thao tác này không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clear()
                    confirmClear = false
                }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryEntity, onFav: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${KIND_LABEL[item.kind] ?: item.kind} · ${Languages.displayName(item.sourceLang)} → " +
                    "${Languages.displayName(item.targetLang)} · ${fmt.format(Date(item.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(item.sourceText, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            Text(item.translatedText, style = MaterialTheme.typography.bodyLarge, maxLines = 6)
            Row {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(item.translatedText)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép")
                }
                IconButton(onClick = { onFav(item.id) }) {
                    Icon(if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Yêu thích")
                }
                IconButton(onClick = { onDelete(item.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Xóa")
                }
            }
        }
    }
}
