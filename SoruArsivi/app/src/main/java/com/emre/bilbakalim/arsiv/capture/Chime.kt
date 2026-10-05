package com.emre.bilbakalim.arsiv.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Sesli uyarı.
 *
 * Telefon cebinizdeyken ya da başka bir işle uğraşırken, botun nerede
 * takıldığını ekrana bakmadan anlamanın tek yolu bu. Sistemin kendi
 * bildirim sesi kullanılıyor: kullanıcının sessize aldığı ya da kendi
 * seçtiği ses neyse o çalıyor, uygulamaya ayrı bir ses dosyası gömmüyoruz.
 *
 * **İki ayrı durum var ve ikisi tek ötüşle karışıyordu:**
 *
 *  • [play] — tek ötüş: *"soruyu okudum ama cevabını bilmiyorum."*
 *    Soru arşivde yok; bot tahmin edecek ya da kararı sana bırakacak.
 *  • [playTwice] — çift ötüş: *"ekranda soru var ama şıkları
 *    okuyamıyorum."* Soru arşivde kayıtlı bile olabilir — nitekim
 *    kullanıcının bildirdiği "arşivde olan soruya bilmiyorum diyor"
 *    durumu tam olarak buydu. Aynı sesi çaldığımız için iki bambaşka
 *    arıza aynı görünüyordu.
 *
 * Ses **medya akışından** çalıyor (oyunun sesiyle aynı ses seviyesi).
 * Eskiden bildirim akışındandı ve hiç duyulmuyordu: telefonların oyun
 * modları (Game Booster, Oyun Alanı...) oyun açıkken bildirim seslerini
 * kısıyor, "Rahatsız Etmeyin" de susturuyor. Oyunu duyuyorsan bunu da
 * duyarsın; sesi kapatmak için medya sesini kısmak yeter.
 */
object Chime {

    @Volatile private var ringtone: Ringtone? = null
    @Volatile private var lastAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Tek ötüş: soru okundu, cevabı arşivde yok. Ses çalınamadıysa sebebi,
     * çalındıysa ya da aralık nedeniyle atlandıysa null.
     */
    fun play(context: Context): String? = burst(context, 1)

    /** Çift ötüş: ekranda soru var ama şıklar okunamıyor. */
    fun playTwice(context: Context): String? = burst(context, 2)

    /**
     * Sesi çalar. Arka arkaya gelen sorularda üst üste binmemesi için
     * [MIN_GAP_MS] aralığı var; bir öbek (tek ya da çift ötüş) tek olay
     * sayılıyor, yani çift ötüşün ikinci sesi bu aralığa takılmıyor.
     */
    private fun burst(context: Context, times: Int): String? {
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MS) return null
        lastAt = now
        val hata = ring(context, interrupt = false)
        for (i in 1 until times) {
            handler.postDelayed({ ring(context, interrupt = true) }, REPEAT_GAP_MS * i)
        }
        return hata
    }

    /**
     * @param interrupt Öbeğin ikinci sesi için true: ilki hâlâ çalıyorsa
     *   kesilip yeniden başlatılıyor, yoksa çift ötüş tek ötüş gibi
     *   duyulurdu.
     */
    private fun ring(context: Context, interrupt: Boolean): String? {
        return runCatching {
            if (ringtone?.isPlaying == true) {
                if (!interrupt) return null
                ringtone?.stop()
            }
            // Bildirim sesi seçilmemişse (bazı telefonlarda "Yok") alarm ya
            // da zil sesine düşülüyor; hiç ses çıkmamasından iyidir.
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return "telefonda bildirim sesi seçili değil"
            val r = RingtoneManager.getRingtone(context, uri)
                ?: return "bildirim sesi açılamadı"
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone = r
            r.play()
            null
        }.getOrElse {
            Log.w(TAG, "Uyarı sesi çalınamadı: ${it.message}")
            it.message ?: it.javaClass.simpleName
        }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private const val TAG = "SoruArsivi/Chime"
    /** İki uyarı öbeği arasındaki en az süre. */
    private const val MIN_GAP_MS = 1200L
    /** Çift ötüşte iki ses arasındaki aralık. */
    private const val REPEAT_GAP_MS = 550L
}
