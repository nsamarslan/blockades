package com.emre.bilbakalim.arsiv.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Otomatik mod — oyunu uygulamanın kendisi oynar.
 *
 * Manuel modda servis ekrana hiç dokunmaz, sadece okur. Otomatik modda ise:
 *   1. Soru ekrana gelip şıklar yerine oturunca şıklardan **birini rastgele**
 *      seçip oraya bir dokunuş jesti gönderir,
 *   2. Tur bitip "Tekrar Oyna" benzeri bir düğme belirdiğinde ona basıp
 *      yeni turu başlatır.
 *
 * Böylece başında beklemeden, oyunun soru havuzu tükenene kadar arşiv
 * kendi kendine dolar. Dokunuş normal bir parmak dokunuşuyla aynı yoldan
 * gider; cevabın rengini okuyan mevcut mantık hiç değişmeden çalışmaya
 * devam eder — kim dokunduğunu ayırt etmesi gerekmiyor.
 *
 * Jest gönderebilmek için erişilebilirlik servisinin
 * `android:canPerformGestures="true"` ile tanımlanmış olması gerekir.
 */
class AutoPlayer(
    private val service: AccessibilityService,
    /** Teşhis ekranındaki tarama günlüğüne satır yazar. */
    private val log: (String) -> Unit
) {

    // "Tekrar oyna" düğmesi yoklaması: aynı düğmeye üst üste basıp
    // duran bir döngüye girmemek için sayaç tutuyoruz.
    @Volatile private var lastButtonAt = 0L
    @Volatile private var lastButtonKey: String? = null
    @Volatile private var sameButtonCount = 0
    @Volatile private var blockedUntil = 0L

    /** Kaç soru otomatik cevaplandı / kaç kez tur yeniden başlatıldı. */
    @Volatile var tapCount = 0
        private set
    @Volatile var restartCount = 0
        private set

    /**
     * Yeni bir soru göründü. Demek ki bastığımız düğme işe yaramış:
     * düğme yoklamasının sayaçlarını sıfırlıyoruz.
     */
    fun noteQuestion() {
        lastButtonKey = null
        sameButtonCount = 0
        blockedUntil = 0L
    }

    /** Mod kapatıldığında ya da servis düştüğünde her şeyi unut. */
    fun reset() {
        noteQuestion()
    }

    /**
     * Hangi şıkka dokunulacağına karar verir.
     *
     * [knownCorrect] verilmişse (soru arşivde var, cevabı biliniyor ve ayar
     * açık) o şık seçilir; yoksa seçim rastgeledir.
     */
    fun pickOption(count: Int, knownCorrect: Int?): Int =
        knownCorrect?.takeIf { it in 0 until count } ?: Random.nextInt(count)

    /**
     * Verilen şıkka dokunur.
     *
     * [longPress] yeniden denemelerde açılıyor: oyun kısa dokunuşu bazen
     * yutuyor, biraz daha uzun basmak onu aşıyor.
     */
    suspend fun tapOption(rects: List<Rect>, index: Int, longPress: Boolean): Boolean {
        val r = rects.getOrNull(index) ?: return false
        val ok = tap(r.centerX(), r.centerY(), longPress)
        if (ok) tapCount++ else Log.w(TAG, "Şıkka dokunulamadı (${'A' + index})")
        return ok
    }

    /** [pressContinue] sonucu. */
    sealed interface Continue {
        /** Basıldı. */
        data class Pressed(val label: String) : Continue
        /** Tanıdık bir düğme yok — çağıran taraf ekranı raporlayabilir. */
        data object NotFound : Continue
        /**
         * Düğme var ama az önce basıldı ya da işe yaramadığı için ara
         * verildi. "Bulunamadı" ile karıştırılmamalı: teşhis günlüğü
         * eskiden bu durumda da "tanınan düğme yok" yazıyor ve ekranda
         * duran düğmeyi görmüyormuşuz gibi gösteriyordu.
         */
        data object Waiting : Continue
    }

    /**
     * Ekranda soru yokken "Tekrar Oyna" benzeri bir düğme arar ve basar.
     *
     * [items] ekran koordinatlarında olmalıdır (OCR kutuları önceden
     * ölçeklenmiş halde gelir). [allowDismiss] yalnızca ekran uzun süredir
     * kımıldamıyorsa açılır: "Atla", "Devam" gibi yazılar soru ekranında da
     * bulunabildiği için, ayrıştırma bir iki saniye takıldı diye soruyu
     * geçmiş olmayalım.
     */
    suspend fun pressContinue(
        items: List<TextItem>,
        screenH: Int,
        allowDismiss: Boolean
    ): Continue {
        // Önce düğmeyi arıyoruz: "bulunamadı" ile "bekliyoruz" ayrımı ancak
        // böyle doğru kurulur.
        val target = findButton(items, screenH, allowDismiss) ?: return Continue.NotFound

        val now = SystemClock.uptimeMillis()
        if (now < blockedUntil || now - lastButtonAt < BUTTON_GAP_MS) return Continue.Waiting

        val key = TurkishText.normalizeKey(target.text)
        if (key == lastButtonKey) {
            sameButtonCount++
            if (sameButtonCount > SAME_BUTTON_LIMIT) {
                blockedUntil = now + BUTTON_COOLDOWN_MS
                sameButtonCount = 0
                log("otomatik: \"${target.text}\" işe yaramadı, ${BUTTON_COOLDOWN_MS / 1000} sn ara veriliyor")
                return Continue.Waiting
            }
        } else {
            lastButtonKey = key
            sameButtonCount = 1
        }

        lastButtonAt = now
        if (!tap(target.bounds.centerX(), target.bounds.centerY(), longPress = false)) {
            return Continue.Waiting
        }
        restartCount++
        return Continue.Pressed(target.text)
    }

    // -----------------------------------------------------------------------

    /**
     * Ekrandaki metinler arasından basılacak düğmeyi seçer.
     *
     * Öncelik sırası: önce açıkça yeni tur başlatan yazılar ("Tekrar Oyna"),
     * sonra araya giren pencereleri kapatan yazılar ("Tamam"). Eşitlikte
     * ekranda daha aşağıda olan kazanır; düğmeler genelde alttadır.
     *
     * "Çıkış", "Hayır" gibi oyunu kapatabilecek hiçbir yazı listede yok —
     * tanımadığı bir düğmeye asla basmaz.
     */
    private fun findButton(
        items: List<TextItem>,
        screenH: Int,
        allowDismiss: Boolean
    ): TextItem? {
        var best: TextItem? = null
        var bestScore = 0
        val minScore = if (allowDismiss) 2 else 3
        for (item in items) {
            // Durum çubuğu ve gezinme çubuğu bölgesine hiç dokunma.
            if (item.bounds.bottom < screenH * 0.05f) continue
            if (item.bounds.top > screenH * 0.98f) continue
            if (item.bounds.width() <= 0 || item.bounds.height() <= 0) continue

            val score = buttonScore(item.text)
            if (score < minScore) continue

            val cur = best
            if (score > bestScore || (score == bestScore && cur != null && item.centerY > cur.centerY)) {
                best = item
                bestScore = score
            }
        }
        return best
    }

    private fun buttonScore(text: String): Int {
        val k = TurkishText.normalizeKey(text)
        if (k.length < 2 || k.length > 28) return 0
        if (PLAY_AGAIN.any { it == k }) return 4
        if (k.length <= 20 && PLAY_AGAIN.any { k.contains(it) }) return 3
        // Kapatma yazılarında yalnızca birebir eşleşme kabul ediliyor.
        // Parça eşleşmesi "Tümünü kapat" düğmesini de yakalıyordu: bot son
        // kullanılanlar ekranında ona basıp oyunu tamamen kapatmıştı.
        if (DISMISS.any { it == k }) return 2
        return 0
    }

    /**
     * Ekrana tek bir dokunuş gönderir.
     *
     * Jest, sistemin dokunma yoluna girer; hedef uygulama bunu normal bir
     * parmak dokunuşundan ayırt etmez. Görünüşte basit olsa da iki incelik var:
     * çağrı ana iş parçacığından yapılmalı ve sonuç geri çağrısı gelmezse
     * (nadiren oluyor) sonsuza kadar beklememek için zaman aşımı gerekiyor.
     */
    private suspend fun tap(x: Int, y: Int, longPress: Boolean): Boolean {
        val (w, h) = ProjectionService.screenSize(service)
        val px = x.coerceIn(1, (w - 2).coerceAtLeast(1)).toFloat()
        val py = y.coerceIn(1, (h - 2).coerceAtLeast(1)).toFloat()

        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(TAP_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val path = Path().apply { moveTo(px, py) }
                    val gesture = GestureDescription.Builder()
                        .addStroke(
                            GestureDescription.StrokeDescription(
                                path, 0L,
                                if (longPress) LONG_TAP_DURATION_MS else TAP_DURATION_MS
                            )
                        )
                        .build()
                    val callback = object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(d: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }
                        override fun onCancelled(d: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    val accepted = runCatching {
                        service.dispatchGesture(gesture, callback, null)
                    }.getOrElse {
                        Log.w(TAG, "Jest gönderilemedi: ${it.message}")
                        false
                    }
                    // Servis jest yetkisi olmadan tanımlanmışsa dispatchGesture
                    // hemen false döner; geri çağrı hiç gelmez.
                    if (!accepted && cont.isActive) cont.resume(false)
                }
            } ?: false
        }
    }

    companion object {
        private const val TAG = "SoruArsivi/Auto"

        /** Dokunuşun ekranda kaldığı süre. */
        private const val TAP_DURATION_MS = 80L
        /** Yeniden denemede daha uzun basılır. */
        private const val LONG_TAP_DURATION_MS = 160L
        /** Geri çağrı gelmezse bu kadar sonra vazgeç. */
        private const val TAP_TIMEOUT_MS = 1500L
        /** İki düğme dokunuşu arasındaki en az süre. */
        private const val BUTTON_GAP_MS = 1600L
        /** Aynı düğmeye üst üste bu kadar basıp sonuç alamazsak ara veririz. */
        private const val SAME_BUTTON_LIMIT = 4
        private const val BUTTON_COOLDOWN_MS = 30_000L

        /**
         * Yeni tur başlatan yazılar. Türkçe karakterler ve boşluklar
         * [TurkishText.normalizeKey] ile silindiği için burada da sadece
         * küçük harf ve rakam var: "TEKRAR OYNA!" -> "tekraroyna".
         */
        private val PLAY_AGAIN = listOf(
            "tekraroyna", "tekrardanoyna", "yenidenoyna", "yenioyun", "yenibiroyun",
            "tekrardene", "yenidendene", "yenidenbasla", "hemenoyna", "oynamayadevam",
            "yenitur", "oyunubaslat", "oyunabasla", "hadibaslayalim", "basla",
            "oyna", "devamet", "sonrakisoru", "sonraki"
        )

        /**
         * Araya giren pencereleri kapatan yazılar. Yeni tur başlatmazlar ama
         * ödül/bilgi penceresi kapanmadan "Tekrar Oyna" görünmüyor.
         * "Çıkış", "Hayır", "Vazgeç" bilerek listede yok.
         */
        private val DISMISS = listOf(
            "tamam", "devam", "kapat", "anladim", "atla", "gec",
            "odulual", "odulutopla", "odulualma", "hayirtesekkurler"
        )
    }
}
