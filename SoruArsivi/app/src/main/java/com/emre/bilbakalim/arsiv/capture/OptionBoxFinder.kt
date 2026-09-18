package com.emre.bilbakalim.arsiv.capture

import android.graphics.Bitmap

/**
 * Şık kutularını **metinden değil, ekranın kendisinden** bulur.
 *
 * Neden gerekti: şık sayısını ve yerini bugüne kadar ML Kit belirliyordu.
 * Kelime şıklarında bu iyi çalışıyor, ama mor zemin üstünde tek başına duran
 * bir "1" ML Kit'in metin/metin-değil sınıflandırıcısı için zayıf bir aday:
 * ya hiç döndürülmüyor ya da dört rakam tek bloğa birleşiyor. İki durumda da
 * ayrıştırıcının eline dört değil sıfır-iki şık geçiyor, soru okunamıyor ve
 * otomatik mod dokunacak şık bulamadan sorunun süresi doluyordu. Büyütme
 * ölçeğini ya da bekleme süresini ayarlamak bunu çözmedi çünkü sorun
 * çözünürlükte değil, "kaç şık var" sorusunu yanlış yere sormamızdaydı.
 *
 * Buradaki ölçüm metne hiç bakmıyor. Şıklar koyu mor zemin üstünde geniş,
 * parlak, yatay haplar olarak çiziliyor; dördü de aynı yükseklikte ve eşit
 * aralıklı. Bu dizilim, içinde ne yazdığından bağımsız olarak ölçülebilir:
 *
 *   1. Şık bandındaki her satırda "hap rengi" piksellerin oranı sayılır.
 *   2. Geniş ve **içi dolu** bir aralık veren satırlar hap satırıdır.
 *      İçi dolu olma şartı joker düğmelerini eliyor: üç altıgen de geniş bir
 *      aralığa yayılıyor ama aralarında mor boşluk var.
 *   3. Ardışık hap satırları bir kutu olur; yüksekliği ve genişliği tutan,
 *      eşit aralıklı olanlar şık kutularıdır. Soru kartı da beyaz ve geniştir
 *      ama dört-beş kat daha yüksek olduğu için yükseklik süzgecinde kalır.
 *
 * Kazanç yalnızca sayı şıkları değil: elde ettiğimiz kutu **hapın tamamı**
 * oluyor. Eskiden `optionRects` OCR metninin sınırlarıydı, yani bir rakam
 * şıkkında 25x50 piksellik bir harf kutusu; renk okuyucu da onu örnekliyordu
 * ve günlüklerde bir şıkkın baskın rengi mor çıkıyordu (kutu hapın üstünde
 * değildi). Gerçek hap kutusuyla "kart oturdu mu", "hangi şık yeşil" ve
 * "nereye dokunayım" ölçümlerinin üçü birden düzeliyor.
 */
object OptionBoxFinder {

    /** Ekranda bulunmuş tek bir şık kutusu (ekran görüntüsü ölçeğinde). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height: Int get() = bottom - top
        val width: Int get() = right - left
        val centerY: Int get() = (top + bottom) / 2
    }

    /** Tek bir tarama satırının ölçümü. */
    data class Row(
        val y: Int,
        /** Satırdaki hap renkli piksellerin tüm genişliğe oranı. */
        val fill: Float,
        /** En soldaki ve en sağdaki hap pikseli (-1: hiç yok). */
        val left: Int,
        val right: Int
    )

    // --- Eşikler: hepsi gerçek ekran görüntülerinden ölçüldü --------------

    /**
     * Hap sayılmak için en düşük parlaklık.
     *
     * Zemin moru RGB(72,40,152) → parlaklık 0.60. Hapın dört hâli de bunun
     * epey üstünde: beyaz 0.97, turkuaz(110,240,220) 0.94,
     * yeşil(70,250,60) 0.98, kırmızı(250,90,90) 0.98.
     */
    private const val PILL_MIN_V = 0.72f
    /** Bu doygunluğun altı renksizdir: beyaz hap. */
    private const val PILL_GRAY_S = 0.18f
    /** Mavi-mor aralığı zemindir; parlak olsa bile hap değildir. */
    private val PILL_BAN_H = 200f..300f

    /** Kaç sütun örneklenecek (ekran genişliğinden bağımsız). */
    private const val COL_SAMPLES = 120
    /** Kaç pikselde bir satır taranacak. */
    internal const val ROW_STEP = 3

    /** Hap satırı: aralığı ekran genişliğinin en az bu kadarı olmalı. */
    private const val ROW_MIN_SPAN = 0.45f
    /** Hap satırı: hap piksellerinin tüm genişliğe oranı. */
    private const val ROW_MIN_FILL = 0.40f
    /**
     * Hap satırı: aralığın **içi** bu oranda dolu olmalı.
     *
     * Ayırt edici ölçü bu. Bir hap satırında aralığın neredeyse tamamı
     * haptır (harfler küçük bir yer kaplar, oran ~0.95). Alttaki üç joker
     * düğmesi de benzer genişlikte bir aralığa yayılır ama aralarında mor
     * boşluk vardır, oran ~0.70'te kalır.
     */
    private const val ROW_MIN_DENSITY = 0.82f

    /** Şık kutusunun ekran yüksekliğine oranı. */
    private const val BOX_MIN_H = 0.035f
    private const val BOX_MAX_H = 0.130f
    /** Şık kutusunun ekran genişliğine oranı. */
    private const val BOX_MIN_W = 0.40f

    /** Yükseklikler ortancadan en fazla bu kadar sapabilir. */
    private const val H_TOLERANCE = 0.22f
    /** Şıklar arası boşluklar ortalamadan en fazla bu kadar sapabilir. */
    private const val GAP_TOLERANCE = 0.30f

    /** Tarama, soru kartını bütünüyle görebilmek için şık bandının üstünden başlar. */
    private const val SCAN_ABOVE = 0.25f
    private const val SCAN_MIN_TOP = 0.15f
    /** Alt sınırdan biraz aşağıya kadar taranır ki son hap kırpılmış görünmesin. */
    private const val SCAN_BELOW = 0.05f

    // ---------------------------------------------------------------------

    /**
     * Şık kutularını bulur. Bulamazsa boş liste döner ve çağıran taraf eski
     * (metin tabanlı) yola düşer — yani bu ölçüm hiçbir şeyi bozamaz, yalnızca
     * işe yaradığında devreye girer.
     */
    fun find(bitmap: Bitmap, optionsTop: Float, optionsBottom: Float): List<Box> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return emptyList()

        val from = ((optionsTop - SCAN_ABOVE).coerceAtLeast(SCAN_MIN_TOP) * h)
            .toInt().coerceIn(0, h - 2)
        val to = ((optionsBottom + SCAN_BELOW) * h).toInt().coerceIn(from + 2, h)
        val step = (w / COL_SAMPLES).coerceAtLeast(1)

        val buf = IntArray(w)
        val rows = ArrayList<Row>((to - from) / ROW_STEP + 1)
        var y = from
        while (y < to) {
            runCatching { bitmap.getPixels(buf, 0, w, 0, y, w, 1) }
                .onFailure { return emptyList() }
            rows.add(measure(y, buf, w, step))
            y += ROW_STEP
        }

        val boxes = boxesOf(rows, w, h)
            // Tarama penceresinin kenarına dayanan kutu kırpılmış demektir;
            // yüksekliği güvenilmez olduğu için ölçüye alınmaz.
            .filter { it.top > from && it.bottom < to }
            .filter { it.centerY >= optionsTop * h && it.centerY <= optionsBottom * h }
        return selectRun(boxes)
    }

    /** Piksel hap rengi mi? (Zemin moru ve koyu geçiş kareleri elenir.) */
    internal fun isPill(r: Int, g: Int, b: Int): Boolean {
        val max = maxOf(r, g, b)
        if (max / 255f < PILL_MIN_V) return false
        val min = minOf(r, g, b)
        val s = (max - min) / max.toFloat()
        if (s <= PILL_GRAY_S) return true
        return hue(r, g, b, max, min) !in PILL_BAN_H
    }

    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
        val d = (max - min).toFloat()
        if (d == 0f) return 0f
        val h = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return if (h < 0f) h + 360f else h
    }

    /** Tek bir satırın hap piksellerini sayar. */
    internal fun measure(y: Int, px: IntArray, width: Int, step: Int): Row {
        var hits = 0
        var seen = 0
        var left = -1
        var right = -1
        var x = 0
        while (x < width) {
            val c = px[x]
            seen++
            if (isPill((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)) {
                hits++
                if (left < 0) left = x
                right = x
            }
            x += step
        }
        return Row(y, if (seen == 0) 0f else hits.toFloat() / seen, left, right)
    }

    /**
     * Satır bir hap satırı mı?
     *
     * Üç şart birlikte aranıyor: aralık geniş olmalı, o aralığın içi dolu
     * olmalı (joker düğmelerini bu eliyor) ve toplam doluluk anlamlı olmalı.
     */
    internal fun isBoxRow(row: Row, screenW: Int): Boolean {
        if (row.left < 0 || screenW <= 0) return false
        val span = (row.right - row.left).toFloat() / screenW
        if (span < ROW_MIN_SPAN) return false
        if (row.fill < ROW_MIN_FILL) return false
        return row.fill / span >= ROW_MIN_DENSITY
    }

    /** Ardışık hap satırlarını kutulara dönüştürür. */
    internal fun boxesOf(rows: List<Row>, screenW: Int, screenH: Int): List<Box> {
        val out = ArrayList<Box>()
        var run = ArrayList<Row>()

        fun kapat() {
            if (run.isNotEmpty()) {
                val lefts = run.map { it.left }.sorted()
                val rights = run.map { it.right }.sorted()
                val box = Box(
                    left = lefts[lefts.size / 2],
                    top = run.first().y,
                    right = rights[rights.size / 2],
                    bottom = run.last().y + ROW_STEP
                )
                if (box.height >= BOX_MIN_H * screenH &&
                    box.height <= BOX_MAX_H * screenH &&
                    box.width >= BOX_MIN_W * screenW
                ) out.add(box)
            }
            run = ArrayList()
        }

        for (row in rows) {
            if (isBoxRow(row, screenW)) run.add(row) else kapat()
        }
        kapat()
        return out
    }

    /**
     * Kutular arasından şık dizisini seçer.
     *
     * Şıklar aynı yükseklikte ve eşit aralıklıdır; ekranda bunlara benzeyen
     * başka bir dizi yok. Listeye yabancı bir kutu karışırsa (soru kartının
     * alt dilimi gibi) dörtlü pencereler arasından **en düzgün** olanı
     * seçiyoruz, ilk tutarlı olanı değil: yabancı kutu tesadüfen eşik içinde
     * kalabiliyor, ama gerçek dörtlünün sapması her zaman daha küçük.
     */
    internal fun selectRun(boxes: List<Box>): List<Box> {
        if (boxes.size < 3) return emptyList()
        val sorted = boxes.sortedBy { it.top }
        if (sorted.size <= 4) return if (spread(sorted) != null) sorted else emptyList()

        var best: List<Box>? = null
        var bestScore = Float.MAX_VALUE
        for (i in 0..sorted.size - 4) {
            val window = sorted.subList(i, i + 4).toList()
            val score = spread(window) ?: continue
            if (score < bestScore) {
                bestScore = score
                best = window
            }
        }
        return best ?: emptyList()
    }

    /** Yükseklikleri ve aralıkları birbirini tutuyor mu? */
    internal fun consistent(boxes: List<Box>): Boolean = spread(boxes) != null

    /**
     * Dizinin düzgünlüğü: yükseklik ve aralık sapmalarının en büyüğü.
     * Eşikleri aşan dizi için null döner. Küçük değer daha düzgün demektir.
     */
    private fun spread(boxes: List<Box>): Float? {
        if (boxes.size < 3) return null
        val heights = boxes.map { it.height }.sorted()
        val median = heights[heights.size / 2].toFloat()
        if (median <= 0f) return null
        val hSapma = heights.maxOf { kotlin.math.abs(it - median) / median }
        if (hSapma > H_TOLERANCE) return null

        val gaps = boxes.map { it.top }.sorted().zipWithNext { a, b -> (b - a).toFloat() }
        if (gaps.any { it <= 0f }) return null
        val avg = gaps.average().toFloat()
        if (avg <= 0f) return null
        val gSapma = gaps.maxOf { kotlin.math.abs(it - avg) / avg }
        if (gSapma > GAP_TOLERANCE) return null
        return maxOf(hSapma, gSapma)
    }
}
