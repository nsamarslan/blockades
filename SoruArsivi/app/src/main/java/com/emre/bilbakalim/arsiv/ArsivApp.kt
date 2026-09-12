package com.emre.bilbakalim.arsiv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ArsivApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Yakalama servisinin sessiz durum bildirimleri"
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "yakalama_durumu"
    }
}
