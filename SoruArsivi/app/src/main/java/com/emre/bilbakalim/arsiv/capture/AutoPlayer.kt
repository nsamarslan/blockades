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
    /** İşe yaramadığı için [blockedUntil]'e kadar basılmayan düğmenin anahtarı. */
    @Volatile private var blockedKey: String? = null

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
        blockedKey = null
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
        /**
         * Basıldı. [pencere] doluysa basılan düğme yeni tur başlatmadı,
         * araya giren bir pencereyi kapattı; değeri pencerenin başlığı.
         */
        data class Pressed(val label: String, val pencere: String? = null) : Continue
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
        allowDismiss: Boolean,
        refillLives: Boolean
    ): Continue {
        val adaylar = buttonCandidates(items).filter { ekranda(it, screenH) }
        val now = SystemClock.uptimeMillis()
        // İşe yaramadığı için ara verilen düğme. Ekranda başka tanıdık bir
        // düğme varsa ona geçiliyor: düğmenin önünü bizim tanımadığımız bir
        // pencere kapatıyor olabilir. Eskiden 30 saniye boyunca hiçbir şeye
        // basılmıyor, sonra aynı düğmeyle yeniden başlanıyordu.
        val haric = blockedKey?.takeIf { now < blockedUntil }
        var pencere: String? = null
        // "Can Kalmadı" penceresi: arkasındaki "Tekrar Oyna" soluk da olsa
        // okunuyor ve bot ona basıyordu; oysa pencere kapanmadan hiçbir şey
        // olmuyor. Ya "Doldur"a basılıyor ya da beklenip hiçbir şeye
        // dokunulmuyor.
        val target = if (adaylar.any { TurkishText.normalizeKey(it.text).contains(CAN_KALMADI) }) {
            if (!refillLives) {
                logOnce("otomatik: can kalmadı, doldurma kapalı (ayar) · bekliyorum")
                return Continue.Waiting
            }
            val doldur = findRefill(adaylar) ?: run {
                logOnce("otomatik: can kalmadı ama \"Doldur\" okunamadı · bekliyorum")
                return Continue.Waiting
            }
            // Altın yetmiyorsa: pencerenin arkasındaki düğmelere yine basılmıyor.
            if (haric != null && TurkishText.normalizeKey(doldur.text).contains(haric)) {
                return Continue.Waiting
            }
            doldur
        } else {
            // Seviye atlama ("Tebrikler!") penceresi tur sonu ekranının üstüne
            // açılıyor. Arkadaki "Tekrar Oyna" soluk da olsa okunuyor, "Devam
            // Et" ile aynı puanı alıyor ve eşitlikte ekranda aşağıda olan
            // kazandığı için bot hep arkadaki düğmeye basıyordu. Pencere
            // kapanmadığı için o dokunuş hiçbir şey yapmıyor; bot dört kez
            // basıp 30 saniye bekliyor ve bunu sonsuza kadar tekrarlıyordu.
            // Pencere görünüyorsa önce onun kendi düğmesine basılıyor.
            val anahtarlar = adaylar.map { TurkishText.normalizeKey(it.text) }
            val pencereDugmesi = pencereDugmesi(anahtarlar, IntArray(adaylar.size) { adaylar[it].centerY })
                ?.takeIf { i -> haric == null || !anahtarlar[i].contains(haric) }
            if (pencereDugmesi != null) {
                pencere = pencereBasligi(anahtarlar, adaylar)
                adaylar[pencereDugmesi]
            } else {
                // Önce düğmeyi arıyoruz: "bulunamadı" ile "bekliyoruz" ayrımı
                // ancak böyle doğru kurulur.
                findButton(adaylar, allowDismiss, haric)
                    ?: return if (haric != null && findButton(adaylar, allowDismiss, null) != null) {
                        Continue.Waiting
                    } else {
                        Continue.NotFound
                    }
            }
        }

        if (now - lastButtonAt < BUTTON_GAP_MS) return Continue.Waiting

        val key = TurkishText.normalizeKey(target.text)
        if (key == lastButtonKey) {
            sameButtonCount++
            if (sameButtonCount > SAME_BUTTON_LIMIT) {
                blockedUntil = now + BUTTON_COOLDOWN_MS
                blockedKey = key
                sameButtonCount = 0
                lastButtonKey = null
                log(
                    "otomatik: \"${target.text}\" işe yaramadı, ${BUTTON_COOLDOWN_MS / 1000} sn " +
                        "ona basılmayacak (ekranda başka düğme varsa ona geçiliyor)"
                )
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
        if (pencere == null) restartCount++
        return Continue.Pressed(target.text, pencere)
    }

    // -----------------------------------------------------------------------

    @Volatile private var sonUyari: String? = null
    @Volatile private var sonUyariAt = 0L

    /** Aynı uyarıyı 30 saniyede bir yazar. */
    private fun logOnce(satir: String) {
        val now = SystemClock.uptimeMillis()
        if (satir == sonUyari && now - sonUyariAt < BUTTON_COOLDOWN_MS) return
        sonUyari = satir
        sonUyariAt = now
        log(satir)
    }

    /** "Can Kalmadı" penceresindeki "Doldur" düğmesi. */
    private fun findRefill(adaylar: List<TextItem>): TextItem? =
        adaylar.filter { TurkishText.normalizeKey(it.text) == DOLDUR }
            .maxByOrNull { it.centerY }

    /** Günlük için pencerenin başlığı: "Tebrikler!", okunamadıysa en üstteki başlık. */
    private fun pencereBasligi(anahtarlar: List<String>, adaylar: List<TextItem>): String =
        adaylar.indices.filter { pencereBasligiMi(anahtarlar[it]) }
            .minWithOrNull(compareBy({ !anahtarlar[it].startsWith("tebrik") }, { adaylar[it].centerY }))
            ?.let { adaylar[it].text.trim().replace('\n', ' ') }
            ?: "pencere"

    private fun ekranda(item: TextItem, screenH: Int): Boolean =
        item.bounds.bottom >= screenH * 0.05f && item.bounds.top <= screenH * 0.98f &&
            item.bounds.width() > 0 && item.bounds.height() > 0

    /**
     * Ekrandaki metinler arasından basılacak düğmeyi seçer.
     *
     * Öncelik sırası: önce açıkça yeni tur başlatan yazılar ("Tekrar Oyna"),
     * sonra araya giren pencereleri kapatan yazılar ("Tamam"). Eşitlikte
     * ekranda daha aşağıda olan kazanır; düğmeler genelde alttadır.
     *
     * "Çıkış", "Hayır" gibi oyunu kapatabilecek hiçbir yazı listede yok —
     * tanımadığı bir düğmeye asla basmaz.
     *
     * [items] önceden [ekranda] süzgecinden geçmiş olmalı: durum çubuğu ve
     * gezinme çubuğu bölgesine hiç dokunulmuyor. Anahtarında [haric] geçen
     * yazılar atlanıyor (işe yaramadığı için ara verilen düğme; "Ana Menü
     * Tekrar Oyna" gibi birleşik satırlarıyla birlikte).
     */
    private fun findButton(
        items: List<TextItem>,
        allowDismiss: Boolean,
        haric: String?
    ): TextItem? {
        var best: TextItem? = null
        var bestScore = 0
        val minScore = if (allowDismiss) 2 else 3
        for (item in items) {
            if (haric != null && TurkishText.normalizeKey(item.text).contains(haric)) continue

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

        /**
         * Düğme adayları: OCR bloğu, satırları ve kısa satırların ardışık
         * kelime dizileri.
         *
         * Neden kelimeler: tur sonu ekranında alttaki düğme yazıları aynı
         * hizada durduğu için OCR "Ana Menü" ile "Tekrar Oyna"yı tek satıra
         * birleştiriyor. Bot o satırı "Tekrar Oyna" sanıp satırın **ortasına**,
         * yani iki düğmenin arasına basıyordu. Kelime dizisi "Tekrar Oyna"
         * birebir eşleştiği için satırın kendisinden yüksek puan alıyor ve
         * dokunuş doğru düğmenin yazısına gidiyor.
         *
         * Kelime dizileri yalnızca kısa satırlardan çıkarılıyor: uzun bir
         * cümlenin içindeki "devam et" gibi bir parça düğme sanılmasın.
         */
        internal fun buttonCandidates(items: List<TextItem>): List<TextItem> {
            val out = ArrayList<TextItem>(items.size * 3)
            for (item in items) {
                out.add(item)
                for (line in item.lines) {
                    if (line.text != item.text) out.add(line)
                    val words = line.lines
                    if (words.size < 2) continue
                    // Satır, kelimeler arasındaki geniş boşluklardan
                    // parçalara bölünüyor: yan yana duran düğmelerin yazıları
                    // ("Kategoriler   Ana Menü   Tekrar Oyna") bazen tek
                    // satıra birleşiyor ve satır uzun olduğu için aşağıdaki
                    // kelime dizileri hiç çıkarılmıyordu — tur sonunda hiçbir
                    // şeye basılmadı. Düğmeler arasındaki boşluk kelime
                    // arasındaki boşluktan çok daha geniş; her parça ayrı bir
                    // düğme yazısı gibi ele alınıyor.
                    val parcalar = bosluklaBol(
                        IntArray(words.size) { words[it].bounds.left },
                        IntArray(words.size) { words[it].bounds.right },
                        IntArray(words.size) { words[it].bounds.height() }
                    )
                    for (parca in parcalar) {
                        val pw = words.subList(parca.first, parca.last + 1)
                        if (parcalar.size > 1) out.add(birlestir(pw))
                        val metin = pw.joinToString(" ") { it.text }
                        if (metin.length > RUN_LINE_MAX_LEN || pw.size < 2) continue
                        for (i in pw.indices) {
                            for (j in i until minOf(pw.size, i + RUN_MAX_WORDS)) {
                                if (i == 0 && j == pw.lastIndex) continue // parçanın kendisi
                                out.add(birlestir(pw.subList(i, j + 1)))
                            }
                        }
                    }
                }
            }
            return out
        }

        private fun birlestir(run: List<TextItem>): TextItem = TextItem(
            run.joinToString(" ") { it.text },
            Rect(
                run.minOf { it.bounds.left },
                run.minOf { it.bounds.top },
                run.maxOf { it.bounds.right },
                run.maxOf { it.bounds.bottom }
            )
        )

        /**
         * Bir satırın kelimelerini, aralarındaki boşluk kelime yüksekliğinin
         * [PARCA_BOSLUK_ORANI] katından genişse ayrı parçalara böler.
         * Kelimeler soldan sağa sıralı gelmeli. Dönen aralıklar kelime
         * indisleridir. `Rect` birim testte çalışmadığı için düz sayılarla.
         */
        internal fun bosluklaBol(sol: IntArray, sag: IntArray, yukseklik: IntArray): List<IntRange> {
            if (sol.isEmpty()) return emptyList()
            val h = yukseklik.sorted()[yukseklik.size / 2].coerceAtLeast(1)
            val out = ArrayList<IntRange>()
            var bas = 0
            for (i in 1 until sol.size) {
                if (sol[i] - sag[i - 1] > h * PARCA_BOSLUK_ORANI) {
                    out.add(bas until i)
                    bas = i
                }
            }
            out.add(bas until sol.size)
            return out
        }

        /** Kelime yüksekliğinin bu katından geniş boşluk, iki ayrı düğme demek. */
        private const val PARCA_BOSLUK_ORANI = 1.5f

        /** Kelime dizisi çıkarılacak satırın en fazla uzunluğu (düğme yazısı gibi kısa). */
        private const val RUN_LINE_MAX_LEN = 28
        /** Bir kelime dizisindeki en fazla kelime. */
        private const val RUN_MAX_WORDS = 4
        /** "Can Kalmadı" (son harfi okunmasa da). */
        private const val CAN_KALMADI = "cankalmad"
        private const val DOLDUR = "doldur"

        /**
         * Bir yazının "tur başlat / pencereyi kapat" düğmesi olma puanı.
         * Companion'da duruyor ki servis örneği olmadan test edilebilsin.
         */
        internal fun buttonScore(text: String): Int {
            val k = TurkishText.normalizeKey(text)
            if (k.length < 2 || k.length > 28) return 0
            if (PLAY_AGAIN.any { it == k }) return 4
            // Tek harflik OCR hatası: "Tekrar Oyna" bir dakika boyunca
            // "Tekrar Oynd" okundu ve tur sonu ekranında hiçbir şeye
            // basılmadı. Yalnızca uzun yazılarda: kısa yazılarda tek harf
            // başka bir kelime demek.
            if (PLAY_AGAIN.any { it.length >= FUZZY_MIN_LEN && TurkishText.sameOptionKey(k, it) }) return 3
            // Parça eşleşmesi yalnızca baş ya da sonda: "En Çok Oynananlar"
            // normalize edilince "encokoynananlar" oluyor ve içinde "oyna"
            // geçtiği için bot lobide o listeye basıp turu geciktiriyordu.
            // "tekraroyna", "hemenoyna", "oynamayadevam" yine eşleşiyor.
            if (k.length <= 20 && PLAY_AGAIN.any { k.startsWith(it) || k.endsWith(it) }) return 3
            // Kapatma yazılarında yalnızca birebir eşleşme kabul ediliyor.
            // Parça eşleşmesi "Tümünü kapat" düğmesini de yakalıyordu: bot son
            // kullanılanlar ekranında ona basıp oyunu tamamen kapatmıştı.
            if (DISMISS.any { it == k }) return 2
            return 0
        }

        /**
         * Araya giren bir pencere (seviye atlama, "Tebrikler!") görünüyorsa
         * onun düğmesinin indisi; pencere yoksa ya da düğmesi okunamadıysa
         * null.
         *
         * Pencerenin düğmesi, en üstteki başlık yazısının altındaki ilk
         * pencere düğmesi ("Devam Et", "Tamam"). Arkadaki tur sonu ekranının
         * düğmeleri ("Tekrar Oyna") bu listede yok; onlara pencere
         * kapanınca sıradan yoldan basılıyor.
         *
         * [anahtarlar] [TurkishText.normalizeKey] ile sadeleşmiş yazılar,
         * [ortaY] kutularının dikey ortası. `Rect` birim testte çalışmadığı
         * için düz sayılarla.
         */
        internal fun pencereDugmesi(anahtarlar: List<String>, ortaY: IntArray): Int? {
            val baslikY = anahtarlar.indices.filter { pencereBasligiMi(anahtarlar[it]) }
                .minOfOrNull { ortaY[it] } ?: return null
            return anahtarlar.indices
                .filter { i -> ortaY[i] > baslikY && pencereDugmesiMi(anahtarlar[i]) }
                .minByOrNull { ortaY[it] }
        }

        /** "Tebrikler!", "SEVİYE 28", "Seviye Atladın" gibi pencere başlıkları. */
        internal fun pencereBasligiMi(k: String): Boolean =
            k.length <= PENCERE_BASLIK_MAX_LEN && PENCERE_BASLIKLARI.any { k.startsWith(it) }

        private fun pencereDugmesiMi(k: String): Boolean =
            PENCERE_DUGMELERI.any { d ->
                k == d || (d.length >= PENCERE_FUZZY_MIN_LEN && TurkishText.sameOptionKey(k, d))
            }

        /**
         * Pencere başlıkları. Yalnızca başta: "Seviye 28" sadeleşince
         * "seviye28" oluyor.
         */
        private val PENCERE_BASLIKLARI = listOf("tebrik", "seviye", "yeniseviye")
        /** Uzun bir cümlenin başındaki "tebrik" pencere başlığı sayılmasın. */
        private const val PENCERE_BASLIK_MAX_LEN = 20

        /**
         * Pencereyi kapatan düğmeler. Bilerek kısa tutuldu: "2x Topla",
         * "Reklam İzle" gibi reklam açan düğmeler yok.
         */
        private val PENCERE_DUGMELERI = listOf("devamet", "devam", "tamam", "kapat")
        /** "Devam Ef" gibi tek harf hatası yalnızca "devamet"te hoş görülüyor. */
        private const val PENCERE_FUZZY_MIN_LEN = 7

        /** Tek harflik hatanın hoş görüldüğü en kısa düğme yazısı. */
        private const val FUZZY_MIN_LEN = 8

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
