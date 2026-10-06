package com.promptflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ScriptEntity::class], version = 1, exportSchema = false)
abstract class PromptFlowDatabase : RoomDatabase() {

    abstract fun scriptDao(): ScriptDao

    companion object {
        @Volatile
        private var INSTANCE: PromptFlowDatabase? = null

        fun getDatabase(context: Context): PromptFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PromptFlowDatabase::class.java,
                    "promptflow_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Insert initial welcome template script
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database.scriptDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: ScriptDao) {
                val sample = ScriptEntity(
                    title = "欢迎使用 PromptFlow (示例台本)",
                    content = """大家好！欢迎体验 PromptFlow 语流提词器。

【防眼神飘移小技巧】
在录制视频时，请留意上方的提词卡片已经贴近前置摄像头打孔区域。
这样阅读台本时，您的眼神几乎直视镜头，成片看起来非常真诚坚定！

【播客与录音模式】
如果您只需要录制音频，请切换到底部【音频录音提词】模式。
该模式彻底关闭相机传感器，手机长时间录音不发热、不掉电，界面还带有实时声波拾音指示！

【外接遥控】
支持蓝牙自拍杆和音量键单击暂停/开始，助您一个人轻松高效录制。""".trimIndent(),
                    speed = 1.0f,
                    fontSizeSp = 18
                )
                dao.insertScript(sample)
            }
        }
    }
}
