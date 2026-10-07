package vn.thuducwater.translator.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import vn.thuducwater.translator.AppContainer
import vn.thuducwater.translator.TdwApp
import vn.thuducwater.translator.engine.TdwException

/** Tạo ViewModel và tiêm AppContainer (MVVM: ViewModel đóng vai controller, màn hình Compose chỉ hiển thị). */
@Composable
inline fun <reified VM : ViewModel> tdwViewModel(noinline create: (AppContainer) -> VM): VM {
    val factory = remember {
        viewModelFactory {
            initializer { create((this[APPLICATION_KEY] as TdwApp).container) }
        }
    }
    return viewModel(factory = factory)
}

/** Thông báo lỗi tiếng Việt từ exception của ML Kit / hệ thống. */
fun friendlyError(prefix: String, e: Throwable): String {
    if (e is TdwException) return "$prefix: ${e.message}"
    val msg = e.message.orEmpty()
    val hint = when {
        msg.contains("network", ignoreCase = true) || msg.contains("download", ignoreCase = true) ->
            "Cần kết nối mạng để tải gói ngôn ngữ lần đầu."
        else -> msg.take(200)
    }
    return "$prefix: $hint"
}
