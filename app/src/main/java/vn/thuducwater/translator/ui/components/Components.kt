package vn.thuducwater.translator.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import vn.thuducwater.translator.data.AUTO
import vn.thuducwater.translator.data.Lang
import vn.thuducwater.translator.data.Languages

/** Nút chọn ngôn ngữ: bấm mở hộp thoại có ô tìm kiếm (gõ "nhật", "anh"...). */
@Composable
fun LanguageButton(code: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, allowAuto: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    // Bỏ chữ "Tiếng " cho gọn (đã rõ là ngôn ngữ), giảm lề trong để tên dài vẫn hiện đủ
    val label = Languages.displayName(code).removePrefix("Tiếng ")
        .replaceFirstChar { it.uppercase() }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    if (open) {
        LanguageDialog(allowAuto = allowAuto, onDismiss = { open = false }, onPick = {
            onChange(it)
            open = false
        })
    }
}

@Composable
private fun LanguageDialog(allowAuto: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val options = remember(allowAuto) { (if (allowAuto) listOf(Lang(AUTO, "Tự nhận diện")) else emptyList()) + Languages.all }
    val filtered = options.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        title = { Text("Chọn ngôn ngữ") },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm ngôn ngữ…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.code }) { lang ->
                        Text(
                            lang.name,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(lang.code) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
    )
}

/** Ngôn ngữ đích hay dùng: bấm 1 lần là đổi, không phải mở danh sách 59 ngôn ngữ. */
val QUICK_TARGETS = listOf(
    "vi" to "Việt", "en" to "Anh", "ja" to "Nhật", "zh" to "Trung", "ko" to "Hàn",
    "fr" to "Pháp", "de" to "Đức", "th" to "Thái", "ru" to "Nga", "es" to "T.B.Nha",
)

@Composable
fun QuickLangRow(selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QUICK_TARGETS.forEach { (code, name) ->
            FilterChip(selected = selected == code, onClick = { onPick(code) }, label = { Text(name) })
        }
    }
}

/** Thanh chọn cặp ngôn ngữ nguồn ⇄ đích. */
@Composable
fun LanguageBar(src: String, tgt: String, onSrc: (String) -> Unit, onTgt: (String) -> Unit, onSwap: () -> Unit, allowAuto: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LanguageButton(src, onSrc, Modifier.weight(1f), allowAuto)
        IconButton(onClick = onSwap) { Icon(Icons.Filled.SwapHoriz, contentDescription = "Đảo chiều") }
        LanguageButton(tgt, onTgt, Modifier.weight(1f))
    }
}

@Composable
fun StatusText(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Thông báo lỗi màu đỏ. */
@Composable
fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Trả về hàm "chạy hành động sau khi có quyền" (hỏi quyền nếu chưa có). */
@Composable
fun rememberPermissionAction(permission: String, onDenied: () -> Unit): ((() -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else onDenied()
        pending = null
    }
    return { action ->
        if (hasPermission(context, permission)) {
            action()
        } else {
            pending = action
            launcher.launch(permission)
        }
    }
}

const val PERM_MIC = Manifest.permission.RECORD_AUDIO
const val PERM_CAMERA = Manifest.permission.CAMERA
