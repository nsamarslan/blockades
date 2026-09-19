package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Şık kutusu ölçümünün testleri.
 *
 * Bu ölçüm, şıkları sayı olan sorularda ("1 / 3 / 4 / 2") tıkanmanın
 * çözümü: şık sayısı artık OCR'ın tek başına duran bir rakamı görmesine
 * bağlı değil. Bu yüzden testler iki şeyi birden korumak zorunda —
 * dört hap bulunmalı, ve hapa benzeyen başka hiçbir şey şık sayılmamalı.
 *
 * Renkler ve oranlar gerçek ekran görüntülerinden: zemin moru
 * RGB(72,40,152); hapın dört hâli beyaz(248,248,248),
 * turkuaz(110,240,220), yeşil(70,250,60), kırmızı(250,90,90).
 */
class OptionBoxFinderTest {

    // --- Piksel sınıflandırma ---------------------------------------------

    @Test
    fun `hapin dort hali de hap sayilir`() {
        assertTrue("beyaz", OptionBoxFinder.isPill(248, 248, 248))
        assertTrue("turkuaz", OptionBoxFinder.isPill(110, 240, 220))
        assertTrue("karar yeşili", OptionBoxFinder.isPill(70, 250, 60))
        assertTrue("doğru yeşili", OptionBoxFinder.isPill(120, 240, 170))
        assertTrue("kırmızı", OptionBoxFinder.isPill(250, 90, 90))
    }

    @Test
    fun `mor zemin hap sayilmaz`() {
        assertFalse("kart zemini", OptionBoxFinder.isPill(72, 40, 152))
        assertFalse("koyu mor", OptionBoxFinder.isPill(45, 25, 100))
        // Ekranın alt tarafındaki açık mor: parlak ama tonu zemin tonu.
        assertFalse("açık mor", OptionBoxFinder.isPill(120, 80, 210))
    }

    @Test
    fun `sik metninin lacivert harfleri hap sayilmaz`() {
        // Hapın üstündeki yazı koyu lacivert; sayılmaması doluluk oranını
        // düşürür ama eşik bunu kaldıracak kadar gevşek.
        assertFalse(OptionBoxFinder.isPill(40, 45, 110))
    }

    // --- Satır ayrımı ------------------------------------------------------

    private fun row(y: Int, fill: Float, left: Int, right: Int) =
        OptionBoxFinder.Row(y, fill, left, right)

    // Aşağıdaki oranlar gerçek ekran görüntüsünden (1080x2400) ölçüldü.

    @Test
    fun `genis ve ici dolu satir hap satiridir`() {
        // Hap ekranın %65-70'ini kaplıyor; üstündeki yazı örnekleme
        // adımından küçük olduğu için doluluk neredeyse tam.
        assertTrue("boş kısım", OptionBoxFinder.isBoxRow(row(1290, 0.68f, 171, 900), 1080))
        assertTrue("yazının olduğu satır", OptionBoxFinder.isBoxRow(row(1338, 0.70f, 162, 918), 1080))
    }

    @Test
    fun `joker duğmeleri hap satiri degildir`() {
        // Üç altıgen ekranın %56'sına yayılıyor ama aralarında mor boşluk
        // var: aralığın içi ancak %64 dolu. Ayırt eden ölçü bu.
        assertFalse("altıgenler", OptionBoxFinder.isBoxRow(row(2180, 0.36f, 234, 837), 1080))
        assertFalse("bedel rozetleri", OptionBoxFinder.isBoxRow(row(2150, 0.29f, 252, 855), 1080))
    }

    @Test
    fun `soru kartinin yazili satiri hap satiri degildir`() {
        // Kart hapdan geniş (%83) ama soru yazısı iri: aralığın içi %74'te
        // kalıyor. Kart bu yüzden tek parça bir kutu olamıyor.
        assertFalse(OptionBoxFinder.isBoxRow(row(900, 0.62f, 90, 990), 1080))
    }

    @Test
    fun `dar satir hap satiri degildir`() {
        // Tepedeki yıldız/altın rozetleri: parlak ama dar.
        assertFalse(OptionBoxFinder.isBoxRow(row(185, 0.20f, 720, 948), 1080))
    }

    @Test
    fun `hic hap pikseli olmayan satir elenir`() {
        assertFalse(OptionBoxFinder.isBoxRow(row(1200, 0f, -1, -1), 1080))
    }

    // --- Kutulara ayırma ---------------------------------------------------
    //
    // Aşağıdaki bütün değerler, kullanıcının gönderdiği iki gerçek ekran
    // görüntüsünden ölçüldü (1080x2400, şıkları "1 / 3 / 4 / 2" ve
    // "4 / 3 / 1 / 2" olan sorular). Haplar y1263-1413, 1470-1620,
    // 1677-1827, 1884-2031; soru kartının yazısız alt dilimi y993-1188;
    // joker şeridi y2223-2244.

    /** Ekran görüntüsünün şık bandını satır satır kurar. */
    private fun bandRows(
        screenH: Int,
        vararg araliklar: IntRange
    ): List<OptionBoxFinder.Row> {
        val rows = ArrayList<OptionBoxFinder.Row>()
        var y = 0
        while (y < screenH) {
            val hap = araliklar.any { y in it }
            rows.add(if (hap) row(y, 0.68f, 171, 909) else row(y, 0f, -1, -1))
            y += OptionBoxFinder.ROW_STEP
        }
        return rows
    }

    @Test
    fun `dort hap dort kutu verir`() {
        val rows = bandRows(2400, 1263..1413, 1470..1620, 1677..1827, 1884..2031)
        val boxes = OptionBoxFinder.boxesOf(rows, screenW = 1080, screenH = 2400)
        assertEquals(4, boxes.size)
        assertTrue("kutu hapın tamamını kapsamalı", boxes.all { it.height in 145..160 })
        assertEquals(4, OptionBoxFinder.selectRun(boxes).size)
    }

    @Test
    fun `ilerleme seridi ve joker seridi yukseklikten elenir`() {
        // Tepedeki "1 2 3 4 5 6 7" şeridi 45 piksel, joker şeridi 21 piksel;
        // hap 150. İkisi de geniş ve parlak, ayıran şey yükseklik.
        val rows = bandRows(2400, 921..966, 1263..1413, 1470..1620, 1677..1827,
            1884..2031, 2223..2244)
        val boxes = OptionBoxFinder.boxesOf(rows, screenW = 1080, screenH = 2400)
        assertEquals(4, boxes.size)
        assertTrue(boxes.all { it.top >= 1263 })
    }

    @Test
    fun `soru kartinin alt dilimi dogru dortluyu bozmaz`() {
        // Kartın yazısız alt dilimi (y993-1188) hapa benzer bir kutu
        // üretiyor. Ayıran ölçü genişlik: kart 900, hap 738 piksel.
        val kart = OptionBoxFinder.Box(90, 993, 990, 1188)
        val haplar = listOf(
            OptionBoxFinder.Box(171, 1263, 909, 1413),
            OptionBoxFinder.Box(171, 1470, 909, 1620),
            OptionBoxFinder.Box(171, 1677, 909, 1827),
            OptionBoxFinder.Box(171, 1884, 909, 2031)
        )
        assertEquals(haplar, OptionBoxFinder.selectRun(listOf(kart) + haplar))
    }

    @Test
    fun `esit araliklı olmayan kutular sik sayilmaz`() {
        val rasgele = listOf(
            OptionBoxFinder.Box(171, 1263, 909, 1413),
            OptionBoxFinder.Box(171, 1470, 909, 1620),
            OptionBoxFinder.Box(171, 2000, 909, 2150)
        )
        assertTrue(OptionBoxFinder.selectRun(rasgele).isEmpty())
    }

    @Test
    fun `iki satira saran sik dizilimi bozmaz`() {
        // Gerçek arıza: "Uluslararası Uzay istasyonu" şıkkı kendi hapında
        // iki satıra sarıyor, o hap diğerlerinin bir buçuk katı yükseklikte
        // oluyordu. Yükseklik eşiği dar olduğu için dizi tutarsız sayılıyor
        // ve HİÇ kutu bulunamıyordu ("kutu:0"); soru arşivde kayıtlı olduğu
        // hâlde "okunamadı" uyarısı çalıyordu.
        //
        // Haplar arası boşluk (57 piksel) hapın yüksekliğinden bağımsız
        // olarak sabit; ölçüm artık ona ve genişliğe bakıyor.
        val haplar = listOf(
            OptionBoxFinder.Box(171, 1263, 909, 1413),
            OptionBoxFinder.Box(171, 1470, 909, 1620),
            OptionBoxFinder.Box(171, 1677, 909, 1827),
            OptionBoxFinder.Box(171, 1884, 909, 2114)
        )
        assertEquals(haplar, OptionBoxFinder.selectRun(haplar))
        assertEquals(haplar, OptionBoxFinder.selectRun(
            listOf(OptionBoxFinder.Box(90, 993, 990, 1188)) + haplar
        ))
    }

    @Test
    fun `farkli genislikteki kutular ayni dizi sayilmaz`() {
        // Genişlik hapın değişmeyen özelliği; sapan kutu şık değildir.
        val karisik = listOf(
            OptionBoxFinder.Box(171, 1263, 909, 1413),
            OptionBoxFinder.Box(171, 1470, 909, 1620),
            OptionBoxFinder.Box(90, 1677, 990, 1827)
        )
        assertTrue(OptionBoxFinder.selectRun(karisik).isEmpty())
    }

    @Test
    fun `uc kutudan azi kabul edilmez`() {
        val iki = listOf(
            OptionBoxFinder.Box(171, 1263, 909, 1413),
            OptionBoxFinder.Box(171, 1470, 909, 1620)
        )
        assertTrue(OptionBoxFinder.selectRun(iki).isEmpty())
    }
}
