package vn.thuducwater.translator.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import vn.thuducwater.translator.feature.friendlyError
import vn.thuducwater.translator.feature.tdwViewModel
import vn.thuducwater.translator.ui.components.ErrorText

private val COMMON = listOf("vi", "en", "ja", "zh", "ko", "fr", "de", "th")

data class SettingsUiState(
    val downloaded: Set<String> = emptySet(),
    val working: Set<String> = emptySet(),
    val error: String? = null,
    val info: String? = null,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                val langs = c.translator.downloadedLanguages()
                _state.update { it.copy(downloaded = langs) }
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyError("Không đọc được danh sách gói", e)) }
            }
        }
    }

    fun toggle(lang: String) {
        val has = lang in _state.value.downloaded
        _state.update { it.copy(working = it.working + lang, error = null) }
        viewModelScope.launch {
            try {
                if (has) c.translator.delete(lang) else c.translator.download(lang)
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyError("Lỗi gói ${Languages.displayName(lang)}", e)) }
            } finally {
                _state.update { it.copy(working = it.working - lang) }
                refresh()
            }
        }
    }

    fun testVoice(lang: String) {
        val sample = mapOf("vi" to "Xin chào, đây là giọng đọc thử.", "en" to "Hello, this is a voice test.")
        val err = c.tts.speak(sample[lang] ?: "Hello", Languages.speechTag(lang))
        _state.update { it.copy(error = err, info = if (err == null) "Đang đọc thử ${Languages.displayName(lang)}" else null) }
    }
}

private fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SettingsScreen() {
    val vm = tdwViewModel { SettingsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gói dịch offline", style = MaterialTheme.typography.titleMedium)
        Text(
            "Mỗi ngôn ngữ ~30 MB, tải một lần (nên dùng Wi-Fi). Sau khi tải, dịch văn bản, ảnh, camera chạy không cần Internet.",
            style = MaterialTheme.typography.bodySmall,
        )
        ErrorText(s.error)
        s.info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                COMMON.forEach { lang ->
                    val has = lang in s.downloaded
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Languages.displayName(lang)}  ${if (has) "✅" else ""}",
                            modifier = Modifier.weight(1f),
                        )
                        if (lang in s.working) {
                            CircularProgressIndicator(Modifier.padding(8.dp))
                        } else {
                            TextButton(onClick = { vm.testVoice(lang) }) { Text("Nghe thử") }
                            OutlinedButton(onClick = { vm.toggle(lang) }) { Text(if (has) "Xóa" else "Tải") }
                        }
                    }
                }
            }
        }

        Text("Nhận dạng giọng nói offline", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android dùng bộ nhận dạng của Google. Để chạy không cần mạng: mở ứng dụng Google → Cài đặt → " +
                "Giọng nói → Nhận dạng giọng nói khi không có mạng → tải Tiếng Việt, English, 日本語, 中文. " +
                "Trên Oppo (ColorOS) nếu thiếu, cập nhật ứng dụng Google và \"Speech Services by Google\" trên CH Play.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { openSafely(context, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }) {
            Text("Mở cài đặt nhập liệu bằng giọng nói")
        }

        Text("Giọng đọc", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cần giọng Tiếng Việt trong \"Dịch vụ chuyển văn bản sang lời nói của Google\". Bấm nút dưới, chọn Google → Cài đặt → Cài đặt dữ liệu giọng nói.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { openSafely(context, Intent("com.android.settings.TTS_SETTINGS")) }) {
            Text("Mở cài đặt đọc văn bản")
        }

        Text("Quyền riêng tư", style = MaterialTheme.typography.titleMedium)
        Text(
            "Văn bản, ảnh, giọng nói được xử lý trên điện thoại. Lịch sử lưu trong bộ nhớ riêng của ứng dụng, " +
                "không sao lưu lên cloud (allowBackup=false). Internet chỉ dùng để tải gói ngôn ngữ.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("TDW Translator 1.0.0 · Công ty CP Cấp nước Thủ Đức", style = MaterialTheme.typography.labelSmall)
    }
}
