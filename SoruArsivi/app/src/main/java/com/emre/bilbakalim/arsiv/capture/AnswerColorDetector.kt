package com.emre.bilbakalim.arsiv.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Şık renklerini okur. Bütün eşikler gerçek ekran görüntülerinden ölçüldü.
 *
 * Oyunun akışı ve ölçülen renkler:
 *
 *   1. Şıkka dokunursun → şıkkın **turkuaz** olur
 *      RGB(110,240,220) ton 171 — "seçtiğin bu", karar HENÜZ YOK.
 *   2. Karar açılır:
 *      • doğru bildiysen şıkkın **karar yeşiline** döner
 *        RGB(120,240,170) ton 145
 *      • bilemediysen şıkkın **kırmızıya** döner RGB(250,90,90) ton 0
 *        ve doğru olan şık **karar yeşiline** döner RGB(70,250,60) ton 117
 *
 * Kritik nokta: turkuaz ile karar yeşilini ayırmak. İkisini tek bir banda
 * koyduğumuzda, dokunuşu karardan ayırmak için zaman beklemek gerekiyordu;
 * ama doğru cevapladığında oyun seni bekletmeden sonraki soruya geçtiği için
 * o bekleme cevabı kaçırıyordu. Ton sınırı 158 ikisini ayırıyor:
 *
 *   85–158  → CORRECT : karar açılmış, doğru cevap bu. Anında kaydedilir.
 *   158–195 → PENDING : dokunduğun şık, karar bekleniyor. Kaydedilmez.
 *
 * Kırmızı yalnızca senin yanlış seçimini gösterir; göründüğü an kararın
 * açıldığı kesindir.
 */
object AnswerColorDetector {

    enum class Tint {
        /** Kararın yeşili — doğru cevap. Görüldüğü an kesindir. */
        CORRECT,
        /** Dokunuş turkuazı — seçtiğin şık, karar henüz açılmadı. */
        PENDING,
        /** Kırmızı — senin yanlış seçimin. */
        WRONG,
        NEUTRAL
    }

    /**
     * Tek bir şık kutusundan çıkan ham ölçüm.
     *
     * Oranlar günlüğe basılabilsin diye saklanıyor: "renk: YESIL · · ·"
     * satırı bir şıkkın neden yeşil sayıldığını söylemiyordu ve yanlış
     * sınıflandırmaları ancak tahmin ederek arayabiliyorduk. Oranlarla
     * birlikte "%26 yeşil" ile "%78 yeşil" arasındaki fark görünür oluyor —
     * ilki geçiş karesi, ikincisi gerçek karar.
     */
    data class Read(
        val tint: Tint,
        val correct: Float,
        val pending: Float,
        val wrong: Float,
        /** Kutunun baskın rengi (metin pikselleri elenmiş hâlde). */
        val color: IntArray?,
        /**
         * Örneklerin ne kadarı parlak (en güçlü kanalı [BRIGHT_MIN_CHANNEL]
         * ve üstü). "Kart çizildi mi" kararının ikinci ayağı; bkz.
         * [RENDERED_MIN_BRIGHT_SHARE].
         */
        val bright: Float = 0f
    ) {
        fun detail(i: Int): String = buildString {
            append('A' + i).append(' ')
            append(when (tint) {
                Tint.CORRECT -> "YESIL"
                Tint.PENDING -> "turkuaz"
                Tint.WRONG -> "KIRMIZI"
                Tint.NEUTRAL -> "-"
            })
            append(" ye%").append((correct * 100).toInt())
            append(" tu%").append((pending * 100).toInt())
            append(" kı%").append((wrong * 100).toInt())
            color?.let { append(" (").append(it[0]).append(',').append(it[1])
                .append(',').append(it[2]).append(')') }
        }
    }

    data class Analysis(
        val tints: List<Tint>,
        /**
         * Süre dolduğunda ekran karardığı için yeşil/kırmızı kalmıyor; doğru
         * cevap yalnızca "diğerlerinden farklı olan" şık olarak ayırt edilir.
         */
        val dimmedReveal: Int? = null,
        /** Şıkların baskın renkleri — teşhis günlüğü için. */
        val colors: List<IntArray> = emptyList(),
        /** Şık başına ham ölçüm — günlükte "neden böyle sınıflandı" için. */
        val reads: List<Read> = emptyList(),
        /** Şık başına parlak örnek oranı ([Read.bright]), [colors] ile aynı sırada. */
        val brights: List<Float> = emptyList()
    ) {
        /**
         * Şık kutuları ekrana oturmuş mu?
         *
         * Bunu [tints] üzerinden dolaylı yoldan çıkarmak bir hataydı:
         * "renkli bir şık varsa kart hazırdır" diyorduk, ama korunmak
         * istediğimiz şey zaten geçiş karesinin yanlışlıkla renkli
         * sayılmasıydı. Kontrol kendi kendini iptal ediyordu. Artık ölçüm
         * doğrudan parlaklığa bakıyor.
         *
         * Baskın renk koyu çıksa bile örneklerin yeterince büyük bir kısmı
         * parlaksa kart çizilmiştir: kutu metnin sınırlarıysa (şık kutusu
         * ekrandan ölçülemediğinde) iri ve sık bir yazıda lacivert harfler
         * beyaz zemini sayıca geçebiliyor. Bkz. [optionsRendered].
         */
        fun cardRendered(minChannel: Int): Boolean =
            colors.size == tints.size && colors.isNotEmpty() &&
                colors.indices.all { i ->
                    val c = colors[i]
                    maxOf(c[0], c[1], c[2]) >= minChannel ||
                        (brights.getOrNull(i) ?: 0f) >= RENDERED_MIN_BRIGHT_SHARE
                }

        /** Şık başına parlak örnek oranı — "kart oturmadı" günlük satırı için. */
        fun brightSummary(): String =
            brights.joinToString(" ") { "%" + (it * 100).toInt() }

        /** Ölçümün tamamı — bir karar yazılırken ya da atlanırken basılır. */
        fun detail(): String = reads.mapIndexed { i, r -> r.detail(i) }.joinToString(" | ")

        private fun onlyIndexOf(t: Tint): Int? =
            tints.indices.filter { tints[it] == t }.singleOrNull()

        /** Kararın yeşiline dönen tek şık: doğru cevap. */
        val correctIndex: Int? get() = onlyIndexOf(Tint.CORRECT)

        /** Turkuaza dönen tek şık: dokunduğun şık, karar beklemede. */
        val pendingIndex: Int? get() = onlyIndexOf(Tint.PENDING)

        /** Kırmızıya dönen şık: senin yanlış seçimin. */
        val wrongIndex: Int? get() = onlyIndexOf(Tint.WRONG)

        /** Kırmızı göründüyse karar kesin açılmıştır. */
        val verdictCertain: Boolean get() = tints.any { it == Tint.WRONG }

        /** Karartılmış ekranda ölçülen renkler — neden bulunamadığını görmek için. */
        fun colorSummary(): String =
            colors.joinToString(" ") { "(${it[0]},${it[1]},${it[2]})" }

        /** Teşhis günlüğü için kısa özet. */
        fun summary(): String = tints.joinToString(" ") {
            when (it) {
                Tint.CORRECT -> "YESIL"
                Tint.PENDING -> "turkuaz"
                Tint.WRONG -> "KIRMIZI"
                Tint.NEUTRAL -> "·"
            }
        }
    }

    private const val SAMPLES_X = 16
    private const val SAMPLES_Y = 8
    private const val INSET = 0.16f
    private const val MIN_RATIO = 0.25f
    private const val OTHERS_MAX_SPREAD = 30.0
    private const val OUTLIER_MIN_GAP = 30.0
    /**
     * Karartılmış ekranda dokunulmamış şıkların en yüksek parlaklığı.
     * Ölçülen gerçek değerler 0.55 ve 0.60 civarında; beyaz şık 0.97.
     */
    private const val DIM_MAX_BRIGHTNESS = 0.75f
    /**
     * Şık kutusunun "çizildi" sayılması için en düşük parlaklık.
     * Oturmuş şıklar 0.97, geçiş kareleri 0.60 civarında.
     */
    private const val RENDERED_MIN_BRIGHTNESS = 0.85f

    /** "Parlak örnek" sayılmak için en güçlü kanalın en düşük değeri (0.85). */
    private const val BRIGHT_MIN_CHANNEL = 217

    /**
     * Baskın renk koyu çıktığında kartın yine de çizilmiş sayılması için
     * parlak örneklerin en düşük oranı.
     *
     * Gerçek ekran görüntüsünden ölçüldü: hapın tamamında %86; en sık
     * yazılmış şıkkın ("Demokratikleşme") metin kutusunda %38-52; geçiş
     * karelerinde hap koyu mor olduğu için ~%0. Eşik ikisinin arasında,
     * metin kutusuna geniş pay bırakarak.
     */
    private const val RENDERED_MIN_BRIGHT_SHARE = 0.25f

    fun analyze(
        bitmap: Bitmap,
        optionRects: List<Rect>,
        screenW: Int,
        screenH: Int
    ): Analysis {
        if (optionRects.isEmpty() || bitmap.width <= 0) return Analysis(emptyList())
        val sx = bitmap.width.toFloat() / screenW.coerceAtLeast(1)
        val sy = bitmap.height.toFloat() / screenH.coerceAtLeast(1)

        val scaled = optionRects.map { r ->
            Rect(
                (r.left * sx).toInt(), (r.top * sy).toInt(),
                (r.right * sx).toInt(), (r.bottom * sy).toInt()
            )
        }
        // Renkler her karede ölçülüyor. Eskiden renkli bir şık bulununca
        // buradan erken dönüyorduk ve `colors` boş kalıyordu; "kart çizildi
        // mi" kontrolü de boş listeyi "hazır" sayıyordu. Sonuç: kartın
        // beliriş animasyonunda yanlışlıkla yeşil okunan bir kare, kartı
        // hazır ilan edip erken dokunuşa yol açıyordu.
        val reads = scaled.map { readOf(bitmap, it) }
        val tints = reads.map { it.tint }
        val colors = reads.mapNotNull { it.color }
        val brights = reads.filter { it.color != null }.map { it.bright }

        // Karartılmış ekran kuralı yalnızca hiçbir şık renkli değilken.
        val dimmed = if (tints.all { it == Tint.NEUTRAL } && colors.size == scaled.size) {
            dimmedOutlier(colors)
        } else null
        return Analysis(tints, dimmed, colors, reads, brights)
    }

    /**
     * Şık kutuları ekrana çizilmiş mi?
     *
     * Soru kartı solarak geliyor; bu sırada metin yarı saydam ve kayar
     * hâlde olduğu için OCR harfleri yanlış okuyor ("yıldıza" yerine
     * "yildza"). Bozuk okunan soru arşive ayrı bir kayıt olarak düşüyor.
     * Kutular oturduğunda bembeyaz oluyor; geçiş kareleri koyu mor.
     *
     * Karar tek başına baskın renge bırakılmıyor. Şık kutuları ekrandan
     * ölçülemediğinde elimizdeki kutu OCR metninin sınırları oluyor ve iri,
     * sık yazılmış bir şıkta ("Demokratikleşme") lacivert harfler beyaz
     * zemini sayıca geçebiliyor: gerçek ekranda 128 örnekten 43 beyaz, 42
     * lacivert. OCR kutusu birkaç piksel oynayınca baskın renk lacivert
     * çıkıyor, kart "çizilmedi" sayılıyor ve soru hiç kaydedilmeden — tek
     * satır günlük bile düşmeden — ekranda bekliyordu. Bu yüzden
     * örneklerin yeterli bir kısmı parlaksa da kart çizilmiş sayılıyor.
     */
    fun optionsRendered(
        bitmap: Bitmap,
        optionRects: List<Rect>,
        screenW: Int,
        screenH: Int
    ): Boolean {
        if (optionRects.isEmpty() || bitmap.width <= 0) return true
        val sx = bitmap.width.toFloat() / screenW.coerceAtLeast(1)
        val sy = bitmap.height.toFloat() / screenH.coerceAtLeast(1)
        return optionRects.all { r ->
            val scaled = Rect(
                (r.left * sx).toInt(), (r.top * sy).toInt(),
                (r.right * sx).toInt(), (r.bottom * sy).toInt()
            )
            val read = readOf(bitmap, scaled)
            val c = read.color ?: return@all true
            brightness(c) >= RENDERED_MIN_BRIGHTNESS || read.bright >= RENDERED_MIN_BRIGHT_SHARE
        }
    }

    fun tintOf(bitmap: Bitmap, rect: Rect): Tint = readOf(bitmap, rect).tint

    fun readOf(bitmap: Bitmap, rect: Rect): Read {
        val r = Rect(rect)
        r.inset((r.width() * INSET).toInt(), (r.height() * INSET).toInt())

        val left = r.left.coerceIn(0, bitmap.width - 1)
        val top = r.top.coerceIn(0, bitmap.height - 1)
        val right = r.right.coerceIn(left + 1, bitmap.width)
        val bottom = r.bottom.coerceIn(top + 1, bitmap.height)
        if (right - left < 4 || bottom - top < 4) return Read(Tint.NEUTRAL, 0f, 0f, 0f, null)

        var correct = 0
        var pending = 0
        var wrong = 0
        var bright = 0
        var total = 0
        val hsv = FloatArray(3)

        val stepX = ((right - left).toFloat() / SAMPLES_X).coerceAtLeast(1f)
        val stepY = ((bottom - top).toFloat() / SAMPLES_Y).coerceAtLeast(1f)

        var y = top.toFloat()
        while (y < bottom) {
            var x = left.toFloat()
            while (x < right) {
                val px = bitmap.getPixel(
                    x.toInt().coerceIn(0, bitmap.width - 1),
                    y.toInt().coerceIn(0, bitmap.height - 1)
                )
                Color.colorToHSV(px, hsv)
                total++
                val maxKanal = maxOf((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
                if (maxKanal >= BRIGHT_MIN_CHANNEL) bright++
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]
                if (v >= 0.25f) {
                    when {
                        s >= 0.35f && h in 85f..158f -> correct++
                        s >= 0.20f && h in 158f..195f -> pending++
                        s >= 0.30f && (h <= 20f || h >= 330f) -> wrong++
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (total == 0) return Read(Tint.NEUTRAL, 0f, 0f, 0f, null)
        val c = correct.toFloat() / total
        val p = pending.toFloat() / total
        val w = wrong.toFloat() / total
        val best = maxOf(c, p, w)
        val tint = when {
            best < MIN_RATIO -> Tint.NEUTRAL
            best == c -> Tint.CORRECT
            best == p -> Tint.PENDING
            else -> Tint.WRONG
        }
        return Read(tint, c, p, w, dominantColor(bitmap, rect), bright.toFloat() / total)
    }

    // --- Süre dolduğunda kararan ekran -------------------------------------

    /** Şıkkın baskın rengi — metin pikselleri sonucu bozmasın diye en sık görülen ton. */
    private fun dominantColor(bitmap: Bitmap, rect: Rect): IntArray? {
        val r = Rect(rect)
        r.inset((r.width() * INSET).toInt(), (r.height() * INSET).toInt())
        val left = r.left.coerceIn(0, bitmap.width - 1)
        val top = r.top.coerceIn(0, bitmap.height - 1)
        val right = r.right.coerceIn(left + 1, bitmap.width)
        val bottom = r.bottom.coerceIn(top + 1, bitmap.height)
        if (right - left < 4 || bottom - top < 4) return null

        val hist = IntArray(16 * 16 * 16)
        val stepX = ((right - left).toFloat() / SAMPLES_X).coerceAtLeast(1f)
        val stepY = ((bottom - top).toFloat() / SAMPLES_Y).coerceAtLeast(1f)
        var y = top.toFloat()
        var count = 0
        while (y < bottom) {
            var x = left.toFloat()
            while (x < right) {
                val px = bitmap.getPixel(
                    x.toInt().coerceIn(0, bitmap.width - 1),
                    y.toInt().coerceIn(0, bitmap.height - 1)
                )
                val rr = (px shr 16 and 0xFF) shr 4
                val gg = (px shr 8 and 0xFF) shr 4
                val bb = (px and 0xFF) shr 4
                hist[(rr shl 8) or (gg shl 4) or bb]++
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return null
        var best = 0
        for (i in hist.indices) if (hist[i] > hist[best]) best = i
        return intArrayOf(
            ((best shr 8) and 0xF) * 16 + 8,
            ((best shr 4) and 0xF) * 16 + 8,
            (best and 0xF) * 16 + 8
        )
    }

    private fun distance(a: IntArray, b: IntArray): Double {
        val dr = (a[0] - b[0]).toDouble()
        val dg = (a[1] - b[1]).toDouble()
        val db = (a[2] - b[2]).toDouble()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun isNearWhite(c: IntArray): Boolean =
        c[0] > 215 && c[1] > 215 && c[2] > 215

    /** 0..1 arası parlaklık — HSV'nin V bileşeni. */
    private fun brightness(c: IntArray): Float = satVal(c).second

    /**
     * HSV'nin doygunluk ve parlaklık bileşenleri.
     *
     * Elle hesaplanıyor: android.graphics.Color birim testlerinde boş taklit
     * olduğu için, karartılmış ekran kuralı cihazsız test edilemiyordu.
     * Hesap zaten iki satır.
     */
    private fun satVal(c: IntArray): Pair<Float, Float> {
        val max = maxOf(c[0], c[1], c[2])
        val min = minOf(c[0], c[1], c[2])
        val v = max / 255f
        val sat = if (max == 0) 0f else (max - min).toFloat() / max
        return sat to v
    }

    /**
     * Süre dolduğunda oyun ekranı karartıp doğru cevabı işaretliyor; o ekranda
     * yeşil de kırmızı da yok. Ölçüm:
     *   dokunulmamış şıklar RGB(80,80,140)  doygunluk 0.43  parlaklık 0.55
     *   doğru cevap         RGB(80,40,100)  doygunluk 0.60  parlaklık 0.39
     *
     * Renk adına göre kural yazmak yerine yapıya bakıyoruz: diğerleri birbirinin
     * tıpatıp aynısıyken tek bir şık ayrışıyorsa doğru cevap odur.
     *
     * **Ekranın gerçekten karartılmış olması şart.** Bu kural eskiden rengin
     * ne kadar koyu olduğuna hiç bakmıyordu; şıklar beyazken bile "biri
     * ötekilerden farklı" deyip süre dolmuş sayabiliyordu. Şıkların
     * belirme/vurgulanma animasyonu tam da bu deseni üretiyor ve sonuç soruya
     * yanlış bir doğru cevap yazmak oluyordu. Artık ayrışan şıkkın *dışındaki*
     * grup koyu değilse karar verilmiyor.
     */
    internal fun dimmedOutlier(colors: List<IntArray>): Int? {
        if (colors.size < 4) return null
        if (colors.count { isNearWhite(it) } > 1) return null

        for (i in colors.indices) {
            val others = colors.filterIndexed { j, _ -> j != i }
            var maxAmongOthers = 0.0
            for (a in others.indices) for (b in a + 1 until others.size) {
                maxAmongOthers = maxOf(maxAmongOthers, distance(others[a], others[b]))
            }
            if (maxAmongOthers > OTHERS_MAX_SPREAD) continue

            // Dokunulmamış şıklar karartılmış olmalı. Değilse bu bir karar
            // ekranı değil, olsa olsa bir animasyon karesidir.
            if (others.any { brightness(it) > DIM_MAX_BRIGHTNESS }) continue

            val gap = others.minOf { distance(colors[i], it) }
            if (gap < OUTLIER_MIN_GAP) continue

            // Ayrışan şık ötekilerden daha koyu ya da daha doygun olmalı.
            // Yön kontrolü bilerek tek taraflı: ekran geçişlerinde şıklar
            // sırayla sönüyor ve bir kare boyunca "üçü koyu, biri parlak"
            // deseni oluşuyor. Bu desene cevap yazmak arşive çöp yazmak olur.
            val (s0, v0) = satVal(colors[i])
            val (s1, v1) = satVal(others[0])
            if (v0 <= v1 - 0.08f || s0 >= s1 + 0.10f) return i
        }
        return null
    }
}
