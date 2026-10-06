package com.promptflow.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.promptflow.app.data.local.PromptFlowDatabase

class PromptFlowApp : Application() {

    lateinit var database: PromptFlowDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = PromptFlowDatabase.getDatabase(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FLOATING_SERVICE_ID,
                getString(R.string.channel_floating_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_floating_service_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_FLOATING_SERVICE_ID = "promptflow_floating_channel"
        lateinit var instance: PromptFlowApp
            private set
    }
}
