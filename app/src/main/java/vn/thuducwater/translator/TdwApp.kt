package vn.thuducwater.translator

import android.app.Application
import android.content.Context
import androidx.room.Room
import vn.thuducwater.translator.data.AppDatabase
import vn.thuducwater.translator.data.HistoryRepository
import vn.thuducwater.translator.data.Prefs
import vn.thuducwater.translator.engine.OcrEngine
import vn.thuducwater.translator.engine.TranslatorEngine
import vn.thuducwater.translator.engine.TtsEngine

/** Dependency Injection thủ công: mọi engine/repository được tạo một lần ở đây. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val db: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "tdw_translator.db").build()

    val history = HistoryRepository(db.historyDao())
    val translator = TranslatorEngine(context)
    val ocr = OcrEngine()
    val tts = TtsEngine(context)
    val prefs = Prefs(context)
}

class TdwApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
