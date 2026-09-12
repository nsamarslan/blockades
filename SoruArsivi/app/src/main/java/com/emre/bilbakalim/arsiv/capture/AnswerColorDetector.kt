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

    data class Analysis(
        val tints: List<Tint>,
        /**
         * Süre dolduğunda ekran karardığı için yeşil/kırmızı kalmıyor; doğru
         * cevap yalnızca "diğerlerinden farklı olan" şık olarak ayırt edilir.
         */
        val dimmedReveal: Int? = null,
        /** Şıkların baskın renkleri — teşhis günlüğü için. */
        val colors: List<IntArray> = emptyList()
    ) {
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
        val tints = scaled.map { tintOf(bitmap, it) }

        // Renkli bir şık varsa normal karar ekranındayız; karartılmış ekran
        // kuralını yalnızca oraya düşmediğimizde deniyoruz.
        if (tints.any { it != Tint.NEUTRAL }) return Analysis(tints)

        // Hiçbir şık renkli değil: süre dolup ekran karartılmış olabilir.
        val colors = scaled.mapNotNull { dominantColor(bitmap, it) }
        if (colors.size != scaled.size) return Analysis(tints)
        return Analysis(tints, dimmedOutlier(colors), colors)
    }

    fun tintOf(bitmap: Bitmap, rect: Rect): Tint {
        val r = Rect(rect)
        r.inset((r.width() * INSET).toInt(), (r.height() * INSET).toInt())

        val left = r.left.coerceIn(0, bitmap.width - 1)
        val top = r.top.coerceIn(0, bitmap.height - 1)
        val right = r.right.coerceIn(left + 1, bitmap.width)
        val bottom = r.bottom.coerceIn(top + 1, bitmap.height)
        if (right - left < 4 || bottom - top < 4) return Tint.NEUTRAL

        var correct = 0
        var pending = 0
        var wrong = 0
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

        if (total == 0) return Tint.NEUTRAL
        val c = correct.toFloat() / total
        val p = pending.toFloat() / total
        val w = wrong.toFloat() / total
        val best = maxOf(c, p, w)
        if (best < MIN_RATIO) return Tint.NEUTRAL
        return when (best) {
            c -> Tint.CORRECT
            p -> Tint.PENDING
            else -> Tint.WRONG
        }
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

    private fun satVal(c: IntArray): Pair<Float, Float> {
        val hsv = FloatArray(3)
        Color.RGBToHSV(c[0], c[1], c[2], hsv)
        return hsv[1] to hsv[2]
    }

    /**
     * Süre dolduğunda oyun ekranı karartıp doğru cevabı işaretliyor; o ekranda
     * yeşil de kırmızı da yok. Ölçüm:
     *   dokunulmamış şıklar RGB(80,80,140)  doygunluk 0.43  parlaklık 0.55
     *   doğru cevap         RGB(80,40,100)  doygunluk 0.60  parlaklık 0.39
     *
     * Renk adına göre kural yazmak yerine yapıya bakıyoruz: diğerleri birbirinin
     * tıpatıp aynısıyken tek bir şık ayrışıyorsa doğru cevap odur.
     */
    private fun dimmedOutlier(colors: List<IntArray>): Int? {
        if (colors.size < 4) return null
        if (colors.count { isNearWhite(it) } > 1) return null

        for (i in colors.indices) {
            val others = colors.filterIndexed { j, _ -> j != i }
            var maxAmongOthers = 0.0
            for (a in others.indices) for (b in a + 1 until others.size) {
                maxAmongOthers = maxOf(maxAmongOthers, distance(others[a], others[b]))
            }
            if (maxAmongOthers > OTHERS_MAX_SPREAD) continue

            val gap = others.minOf { distance(colors[i], it) }
            if (gap < OUTLIER_MIN_GAP) continue

            val (s0, v0) = satVal(colors[i])
            val (s1, v1) = satVal(others[0])
            if (v0 <= v1 - 0.08f || s0 >= s1 + 0.10f) return i
        }
        return null
    }
}
