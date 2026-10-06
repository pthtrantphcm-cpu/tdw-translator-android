package vn.thuducwater.translator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import vn.thuducwater.translator.feature.camera.CameraScreen
import vn.thuducwater.translator.feature.conversation.ConversationScreen
import vn.thuducwater.translator.feature.history.HistoryScreen
import vn.thuducwater.translator.feature.ocr.OcrScreen
import vn.thuducwater.translator.feature.settings.SettingsScreen
import vn.thuducwater.translator.feature.text.TextScreen
import vn.thuducwater.translator.feature.voice.VoiceScreen
import vn.thuducwater.translator.ui.theme.TdwTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TdwTheme { AppRoot() } }
    }
}

private data class Tab(val title: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Dịch văn bản", "Văn bản", Icons.Filled.Translate),
    Tab("Dịch giọng nói", "Giọng nói", Icons.Filled.Mic),
    Tab("Hội thoại trực tiếp", "Hội thoại", Icons.Filled.Forum),
    Tab("Dịch ảnh (OCR)", "Ảnh", Icons.Filled.Image),
    Tab("Dịch qua camera", "Camera", Icons.Filled.CameraAlt),
    Tab("Lịch sử & Yêu thích", "Lịch sử", Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Cài đặt" else TABS[tab].title) },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showSettings) {
                SettingsScreen()
            } else {
                when (tab) {
                    0 -> TextScreen()
                    1 -> VoiceScreen()
                    2 -> ConversationScreen()
                    3 -> OcrScreen()
                    4 -> CameraScreen()
                    else -> HistoryScreen()
                }
            }
        }
    }
}
