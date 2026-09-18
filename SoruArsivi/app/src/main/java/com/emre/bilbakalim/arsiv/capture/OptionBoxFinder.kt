package com.emre.bilbakalim.arsiv.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Şık kutularını metinden değil, **görüntüden** bulur.
 *
 * Neden gerekti: şıkları tamamen sayı olan sorularda ("4 / 3 / 2 / 1")
 * ML Kit yalıtık rakam bloğunu sıkça düşürüyor. Şık şeridinin tamamını
 * kırpıp büyütmek bir yere kadar yardım ediyor — şeridin içinde dört ufak
 * rakam duruyor ve blok bulucu yine kaçırabiliyor. Rakamı tek başına,
 * kendi kutusuyla ve büyütülmüş olarak vermek çok daha güvenilir.
 *
 * Kutular şu işaretten bulunuyor: oyunun şıkları koyu zemin üzerinde
 * bembeyaz, ekranın enine yayılmış şeritlerdir. Satır satır "bu satırın
 * ne kadarı parlak" diye bakıp, kesintisiz parlak aralıkları çıkarıyoruz.
 *
 * Kutu bulmak yalnızca OCR'a daha iyi bir kırpma vermek için kullanılıyor;
 * metni okunamayan bir kutu şık sayılmıyor. Yoksa arşive metinsiz, sırf
 * konumdan ibaret kayıtlar düşerdi.
 */
object OptionBoxFinder {

    /** Kutu sayılmak için satırın en az bu kadarı parlak olmalı. */
    private const val MIN_BRIGHT_RATIO = 0.55f
    /** "Parlak" eşiği — oturmuş şıklar 0.97, koyu mor zemin 0.35 civarı. */
    private const val MIN_BRIGHTNESS = 0.80f
    /** Satır başına kaç yatay örnek alınır. */
    private const val X_SAMPLES = 24
    /** Şeridin yüksekliğine göre en fazla kaç satır örneklenir. */
    private const val MAX_ROW_SAMPLES = 240

    /**
     * [top]–[bottom] arasındaki şık kutularını döndürür (bitmap koordinatı).
     *
     * Bulunamazsa boş liste döner; çağıran taraf o zaman eski yola düşer.
     */
    fun find(bitmap: Bitmap, top: Int, bottom: Int, want: Int = 4): List<Rect> {
        val y0 = top.coerceIn(0, bitmap.height - 1)
        val y1 = bottom.coerceIn(y0 + 2, bitmap.height)
        val h = y1 - y0
        if (h < 8 || bitmap.width < 8) return emptyList()

        val step = (h / MAX_ROW_SAMPLES).coerceAtLeast(1)
        val rows = h / step
        if (rows < 8) return emptyList()

        val bright = BooleanArray(rows) { i -> rowIsBright(bitmap, y0 + i * step) }

        // Kutu yüksekliği: ekranın çok küçük bir parçası olamaz (rozet),
        // şeridin tamamı da olamaz (soru kartının alt ucu).
        val minRun = ((h * 0.05f) / step).toInt().coerceAtLeast(2)
        val maxRun = ((h * 0.40f) / step).toInt().coerceAtLeast(minRun + 1)

        return runsOf(bright, minRun, maxRun, want).map { run ->
            val boxTop = y0 + run.first * step
            val boxBottom = (y0 + (run.last + 1) * step).coerceAtMost(y1)
            // Yatay sınırlar kutunun ÜST çeyreğinden ölçülüyor: orada kutu
            // baştan sona düz beyaz, metin aşağıda kalıyor.
            val probeY = boxTop + (boxBottom - boxTop) / 4
            val (left, right) = horizontalExtent(bitmap, probeY)
            Rect(left, boxTop, right, boxBottom)
        }.filter { it.width() > bitmap.width / 4 && it.height() >= 8 }
    }

    /**
     * Parlaklık profilinden kutu aralıklarını çıkarır.
     *
     * Şeridin en üstüne ya da en altına yapışık aralıklar atılıyor: onlar
     * kırpılmış demektir, yani kutu değil, şeride taşan başka bir şeydir
     * (soru kartının beyaz alt ucu gibi). Gerçek bir şık kutusunun altı da
     * üstü de koyu zeminle çevrilidir.
     */
    internal fun runsOf(
        bright: BooleanArray,
        minRun: Int,
        maxRun: Int,
        want: Int
    ): List<IntRange> {
        if (bright.size < 3) return emptyList()
        val runs = ArrayList<IntRange>()
        var start = -1
        for (i in bright.indices) {
            if (bright[i]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                runs.add(start..i - 1)
                start = -1
            }
        }
        if (start >= 0) runs.add(start..bright.lastIndex)

        val kenarsiz = runs.filter { it.first > 0 && it.last < bright.lastIndex }
        val uygun = kenarsiz.filter { (it.last - it.first + 1) in minRun..maxRun }
        if (uygun.size <= want) return uygun

        // Fazla aday var: yükseklikleri ortancaya en yakın olanları tut.
        // Şık kutuları birbirinin aynısıdır; araya karışan şey (rozet,
        // kart kenarı) boyuyla ele veriyor.
        val heights = uygun.map { it.last - it.first + 1 }.sorted()
        val median = heights[heights.size / 2]
        return uygun
            .sortedBy { kotlin.math.abs((it.last - it.first + 1) - median) }
            .take(want)
            .sortedBy { it.first }
    }

    private fun rowIsBright(bitmap: Bitmap, y: Int): Boolean {
        val row = y.coerceIn(0, bitmap.height - 1)
        // Kenarlardan uzak duruyoruz: kutular tam kenara dayanmıyor ve
        // ekranın kenarında gölge/kavis var.
        val from = bitmap.width * 15 / 100
        val to = bitmap.width * 85 / 100
        val span = (to - from).coerceAtLeast(1)
        var parlak = 0
        for (i in 0 until X_SAMPLES) {
            val x = (from + span * i / X_SAMPLES).coerceIn(0, bitmap.width - 1)
            if (brightness(bitmap.getPixel(x, row)) >= MIN_BRIGHTNESS) parlak++
        }
        return parlak.toFloat() / X_SAMPLES >= MIN_BRIGHT_RATIO
    }

    /** Verilen satırda kutunun soldan sağa uzandığı aralık. */
    private fun horizontalExtent(bitmap: Bitmap, y: Int): Pair<Int, Int> {
        val row = y.coerceIn(0, bitmap.height - 1)
        val mid = bitmap.width / 2
        var left = mid
        while (left > 0 && brightness(bitmap.getPixel(left - 1, row)) >= MIN_BRIGHTNESS) left--
        var right = mid
        while (right < bitmap.width - 1 &&
            brightness(bitmap.getPixel(right + 1, row)) >= MIN_BRIGHTNESS
        ) right++
        return left to (right + 1)
    }

    private fun brightness(px: Int): Float =
        maxOf(Color.red(px), Color.green(px), Color.blue(px)) / 255f
}
