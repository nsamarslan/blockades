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

    /**
     * Kutu bulunamadıysa sebebi, teşhis günlüğü için.
     *
     * Bu alan olmadan günlükte yalnızca "kutu:0" yazıyordu ve bu, eski
     * "rozetten başka şık yok" mesajı kadar sessizdi: ölçümün ekranı hiç mi
     * göremediğini, kutuları bulup dizilimi mi beğenmediğini söylemiyordu.
     */
    @Volatile var lastReason: String? = null
        private set

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

    /**
     * Genişlikler ortancadan en fazla bu kadar sapabilir.
     *
     * **Asıl ayırt edici ölçü bu.** Şık hapları her zaman aynı genişlikte
     * çiziliyor; soru kartı ise belirgin biçimde daha geniş (ölçülen gerçek
     * ekranda hap 738, kart 900 piksel — %22 fark). Yükseklik bu işi
     * göremiyor, çünkü uzun bir şık kendi hapında iki satıra sarıyor ve o
     * hap diğerlerinden yüksek oluyor.
     */
    private const val W_TOLERANCE = 0.12f
    /**
     * Yükseklik, ortancanın bu aralığında olmalı.
     *
     * Üst sınır bilerek geniş: iki satıra saran bir şık ("Uluslararası Uzay
     * istasyonu") diğerlerinin iki katı yükseklikte olabiliyor. Eskiden
     * sınır dardı ve böyle bir soruda dizi tutarsız sayılıp **hiç kutu
     * bulunamıyordu** — günlükteki "kutu:0" satırları bundan.
     */
    private val H_RANGE = 0.70f..2.40f
    /** Haplar arası boşluk ortalamadan en fazla bu kadar sapabilir. */
    private const val GAP_TOLERANCE = 0.35f

    /**
     * Yazı satırının kenarları üstteki hap satırından en fazla bu kadar
     * sapabilir (ekran genişliğine oran). Örnekleme adımı genişliğin
     * 1/120'si; bu iki adım kadar pay.
     */
    private const val TEXT_EDGE_TOLERANCE = 0.02f

    /**
     * Bir hapın içinde üst üste gelebilecek yazı satırlarının en fazla
     * yüksekliği (ekran yüksekliğine oran). Tek satırlık şıkta yazı ~50
     * piksel (%2); iki satıra saranın satırları arasında yine hap satırı
     * olduğu için sayaç sıfırlanıyor.
     */
    private const val TEXT_MAX_H = 0.06f

    /**
     * Taranan bant — ayarlardaki şık bölgesinden **bağımsız**.
     *
     * Sabit oranlara bağlamak bir arıza kaynağıydı: soru üç satır olunca
     * şıklar aşağı kayıyor ve dördüncü hap `optionsBottom` (%90) sınırının
     * altında kalıyordu. Geometri zaten kendi kendini doğruluyor — aynı
     * genişlikte, eşit aralıklı dörtlü — o yüzden bant cömert tutuluyor ve
     * kararı dizilim veriyor.
     */
    private const val SCAN_TOP = 0.28f
    private const val SCAN_BOTTOM = 0.99f

    // ---------------------------------------------------------------------

    /**
     * Şık kutularını bulur. Bulamazsa boş liste döner ve çağıran taraf eski
     * (metin tabanlı) yola düşer — yani bu ölçüm hiçbir şeyi bozamaz, yalnızca
     * işe yaradığında devreye girer.
     */
    fun find(bitmap: Bitmap): List<Box> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) { lastReason = "ekran görüntüsü çok küçük"; return emptyList() }

        val from = (SCAN_TOP * h).toInt().coerceIn(0, h - 2)
        val to = (SCAN_BOTTOM * h).toInt().coerceIn(from + 2, h)
        val step = (w / COL_SAMPLES).coerceAtLeast(1)

        val buf = IntArray(w)
        val rows = ArrayList<Row>((to - from) / ROW_STEP + 1)
        var y = from
        while (y < to) {
            runCatching { bitmap.getPixels(buf, 0, w, 0, y, w, 1) }
                .onFailure { lastReason = "piksel okunamadı"; return emptyList() }
            rows.add(measure(y, buf, w, step))
            y += ROW_STEP
        }

        val hapSatiri = rows.count { isBoxRow(it, w) }
        val hepsi = boxesOf(rows, w, h)
        // Tarama penceresinin kenarına dayanan kutu kırpılmış demektir;
        // yüksekliği güvenilmez olduğu için ölçüye alınmaz.
        val boxes = hepsi.filter { it.top > from && it.bottom < to }
        val secilen = selectRun(boxes)
        lastReason = when {
            secilen.isNotEmpty() -> null
            hapSatiri == 0 -> "hap rengi satır yok (ekran koyu ya da kart henüz çizilmedi)"
            boxes.isEmpty() -> "$hapSatiri hap satırı var ama hiçbiri kutu ölçüsünü tutmuyor"
            else -> "${boxes.size} aday kutu var, eşit aralıklı dörtlü yok · " +
                boxes.joinToString(" ") { "${it.width}x${it.height}@${it.top}" }
        }
        return secilen
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

    /**
     * Hapın ortasından geçen yazı satırı mı?
     *
     * Yazının geçtiği satırlarda lacivert harfler hap pikseli sayılmadığı
     * için doluluk düşüyor: "Demokratikleşme" gibi iri ve sık yazılmış bir
     * şıkta %64'e, "İnsanın amacı"nda bile %68'e iniyor — hap satırı eşiği
     * (%82) altında. Satır o zaman hap satırı sayılmıyor ve **her hap ikiye
     * bölünüyordu**: iki yarım da (63 piksel) kutu alt sınırının (84)
     * altında kaldığı için kelime şıklarında hiç kutu bulunamıyor, eski
     * metin yoluna düşülüyordu ("kutu:0"). Rakam şıklarında bu olmuyordu,
     * çünkü tek bir rakam satırın ancak küçük bir kısmını kaplıyor.
     *
     * Yazı satırının ayırt edici işareti kenarları: hapın iki ucu beyaz
     * kaldığı için satırın en sol ve en sağ hap pikseli, hemen üstteki hap
     * satırınınkiyle aynı yerde. Haplar arasındaki mor şeritte hap pikseli
     * hiç yok; joker düğmeleri ise hapa hiç bitişik değil. Yani bu kural
     * iki ayrı hapı birbirine bağlayamıyor.
     */
    internal fun isTextRow(row: Row, above: Row, screenW: Int): Boolean {
        if (row.left < 0 || above.left < 0 || screenW <= 0) return false
        val tol = TEXT_EDGE_TOLERANCE * screenW
        return kotlin.math.abs(row.left - above.left) <= tol &&
            kotlin.math.abs(row.right - above.right) <= tol
    }

    /** Ardışık hap satırlarını kutulara dönüştürür. */
    internal fun boxesOf(rows: List<Row>, screenW: Int, screenH: Int): List<Box> {
        val out = ArrayList<Box>()
        var run = ArrayList<Row>()
        // Hapın içindeki yazı satırları. Kutuya ancak arkasından yeniden bir
        // hap satırı gelirse katılıyorlar; gelmezse kutu son hap satırında
        // kapanıyor, yani kutunun sınırları yine yalnızca hap satırlarından.
        var yazi = 0
        val yaziSiniri = (TEXT_MAX_H * screenH / ROW_STEP).toInt().coerceAtLeast(1)

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
            when {
                isBoxRow(row, screenW) -> {
                    yazi = 0
                    run.add(row)
                }
                run.isNotEmpty() && yazi < yaziSiniri && isTextRow(row, run.last(), screenW) ->
                    yazi++
                else -> {
                    yazi = 0
                    kapat()
                }
            }
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

    /** Genişlikleri ve aralıkları birbirini tutuyor mu? */
    internal fun consistent(boxes: List<Box>): Boolean = spread(boxes) != null

    /**
     * Dizinin düzgünlüğü: genişlik ve aralık sapmalarının en büyüğü.
     * Eşikleri aşan dizi için null döner. Küçük değer daha düzgün demektir.
     *
     * Ölçülen iki şey var ve ikisi de yükseklikten bağımsız:
     *
     *  • **Genişlik** — haplar aynı genişlikte çizilir; soru kartı daha
     *    geniştir. Kartı eleyen ölçü bu.
     *  • **Haplar arası boşluk** — iki hap arasındaki mor şerit her zaman
     *    aynı. Bilerek üstten üste (top-to-top) değil, alttan üste
     *    (bottom-to-top) ölçülüyor: iki satıra saran bir şık kendi hapını
     *    büyütüyor ve üstten üste ölçüm o zaman bozuluyor, aradaki boşluk
     *    ise bozulmuyor.
     *
     * Yükseklik yalnızca kaba bir akıl sağlığı sınırı olarak duruyor.
     */
    private fun spread(boxes: List<Box>): Float? {
        if (boxes.size < 3) return null
        val sorted = boxes.sortedBy { it.top }

        val widths = sorted.map { it.width }.sorted()
        val wMedian = widths[widths.size / 2].toFloat()
        if (wMedian <= 0f) return null
        val wSapma = widths.maxOf { kotlin.math.abs(it - wMedian) / wMedian }
        if (wSapma > W_TOLERANCE) return null

        val heights = sorted.map { it.height }.sorted()
        val hMedian = heights[heights.size / 2].toFloat()
        if (hMedian <= 0f) return null
        if (heights.any { it / hMedian !in H_RANGE }) return null

        val gaps = sorted.zipWithNext { a, b -> (b.top - a.bottom).toFloat() }
        if (gaps.any { it <= 0f }) return null
        val avg = gaps.average().toFloat()
        if (avg <= 0f) return null
        val gSapma = gaps.maxOf { kotlin.math.abs(it - avg) / avg }
        if (gSapma > GAP_TOLERANCE) return null

        return maxOf(wSapma, gSapma)
    }
}
