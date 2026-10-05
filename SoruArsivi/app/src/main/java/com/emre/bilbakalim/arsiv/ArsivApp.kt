package com.emre.bilbakalim.arsiv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ArsivApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.emre.bilbakalim.arsiv.data.AnahtarKotasi.baslat(this)
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
            // Sesli ve öne çıkan ayrı kanal: "hızlı yakalama kapandı" gibi
            // kullanıcının hemen görmesi gereken uyarılar.
            val uyari = NotificationChannel(
                UYARI_CHANNEL_ID,
                "Uyarılar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ekran yakalama kapandığında haber verir"
            }
            getSystemService(NotificationManager::class.java)?.apply {
                createNotificationChannel(channel)
                createNotificationChannel(uyari)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "yakalama_durumu"
        const val UYARI_CHANNEL_ID = "uyarilar"
    }
}
