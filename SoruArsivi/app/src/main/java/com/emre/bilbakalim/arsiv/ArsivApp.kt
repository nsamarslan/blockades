package com.emre.bilbakalim.arsiv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.emre.bilbakalim.arsiv.util.OtomatikYedek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ArsivApp : Application() {

    private val yedekIsi = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        com.emre.bilbakalim.arsiv.data.AnahtarKotasi.baslat(this)
        // Günlük yedek: uygulama açılınca ve süreç yaşadıkça (erişilebilirlik
        // servisi gün boyu açık kalıyor) saatte bir "bugün alındı mı" bakılıyor.
        OtomatikYedek.yukle(this)
        yedekIsi.launch {
            while (true) {
                delay(YEDEK_ILK_BEKLEME_MS)
                runCatching { OtomatikYedek.gerekirseYedekle(this@ArsivApp) }
                delay(YEDEK_ARALIGI_MS - YEDEK_ILK_BEKLEME_MS)
            }
        }
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
        /** Açılışı yavaşlatmasın: ilk yedek kontrolü biraz sonra. */
        private const val YEDEK_ILK_BEKLEME_MS = 30_000L
        private const val YEDEK_ARALIGI_MS = 60 * 60_000L
    }
}
