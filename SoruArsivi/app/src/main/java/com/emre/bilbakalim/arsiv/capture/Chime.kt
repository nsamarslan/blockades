package com.emre.bilbakalim.arsiv.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.SystemClock
import android.util.Log

/**
 * "Bu sorunun cevabını bilmiyoruz" uyarı sesi.
 *
 * Telefon cebinizdeyken ya da başka bir işle uğraşırken, botun hangi soruda
 * tahmin ettiğini ekrana bakmadan anlamanın tek yolu bu. Sistemin kendi
 * bildirim sesi kullanılıyor: kullanıcının sessize aldığı ya da kendi seçtiği
 * ses neyse o çalıyor, uygulamaya ayrı bir ses dosyası gömmüyoruz.
 *
 * Ses **bildirim akışından** çalıyor; telefon sessizdeyse duyulmaz, ki
 * beklenen davranış budur.
 */
object Chime {

    @Volatile private var ringtone: Ringtone? = null
    @Volatile private var lastAt = 0L

    /**
     * Sesi çalar. Arka arkaya gelen sorularda üst üste binmemesi için
     * [MIN_GAP_MS] aralığı var; ayrıca çalan bir ses varsa yenisi
     * başlatılmıyor.
     */
    fun play(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MS) return
        lastAt = now
        runCatching {
            val onceki = ringtone
            if (onceki?.isPlaying == true) return
            // Her Ringtone kendi MediaPlayer'ını tutuyor. Eskisi bırakılmadan
            // üzerine yazılınca her uyarıda bir tane daha birikiyordu; uzun
            // turlarda bu, ses yolunu tüketip uygulamayı ağırlaştırıyor.
            runCatching { onceki?.stop() }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val r = RingtoneManager.getRingtone(context, uri) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone = r
            r.play()
        }.onFailure { Log.w(TAG, "Uyarı sesi çalınamadı: ${it.message}") }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private const val TAG = "SoruArsivi/Chime"
    /** İki uyarı arasındaki en az süre. */
    private const val MIN_GAP_MS = 1200L
}
