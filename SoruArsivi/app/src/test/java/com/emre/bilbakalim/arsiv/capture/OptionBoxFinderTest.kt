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
    // --- Hapın içindeki yazı satırları ---------------------------------------
    //
    // Aşağıdaki değerler "Hangisi Orta Çağ felsefesinin temel konularından
    // değildir?" sorusunun gerçek ekran görüntüsünden (1080x2400) ölçüldü.
    // Şıklar kelime: "İnsanın amacı", "Yaratılışın anlamı", "İnsanın doğası",
    // "Demokratikleşme". Haplar y1263-1413, 1470-1620, 1677-1827, 1884-2031;
    // yazının geçtiği satırlarda doluluk %45-57'ye, aralığın içindeki oran
    // %64-80'e iniyor (hap satırı eşiği %82), kenarlar ise hapın kenarında
    // (162..918) kalıyor.

    /** Ortasından yazı geçen hapları ve soru kartını satır satır kurar. */
    private fun yaziliBant(
        haplar: List<IntRange>,
        yazilar: List<IntRange>,
        kart: IntRange? = null,
        kartYazilari: List<IntRange> = emptyList()
    ): List<OptionBoxFinder.Row> {
        val rows = ArrayList<OptionBoxFinder.Row>()
        var y = 0
        while (y < 2400) {
            rows.add(
                when {
                    yazilar.any { y in it } -> row(y, 0.50f, 162, 918)
                    haplar.any { y in it } -> row(y, 0.70f, 162, 918)
                    kartYazilari.any { y in it } -> row(y, 0.55f, 90, 990)
                    kart != null && y in kart -> row(y, 0.83f, 90, 990)
                    else -> row(y, 0f, -1, -1)
                }
            )
            y += OptionBoxFinder.ROW_STEP
        }
        return rows
    }

    private val haplar = listOf(1263..1412, 1470..1619, 1677..1826, 1884..2030)
    private val yazilar = listOf(1326..1350, 1533..1557, 1740..1764, 1935..1968)

    @Test
    fun `yazinin gectigi satir tek basina hap satiri degildir`() {
        // Arızanın kendisi: "Demokratikleşme"nin ortasından geçen satır.
        assertFalse(OptionBoxFinder.isBoxRow(row(1950, 0.45f, 162, 918), 1080))
        assertFalse(OptionBoxFinder.isBoxRow(row(1329, 0.47f, 162, 918), 1080))
    }

    @Test
    fun `kelime siklarinda yazi hapi ikiye bolmez`() {
        // Eskiden her hap yazının iki yanında 63 piksellik iki yarıma
        // bölünüyor, ikisi de kutu alt sınırının (84) altında kaldığı için
        // hiç kutu bulunamıyordu ("kutu:0"). Bot o zaman metin kutularıyla
        // çalışıyor ve "Demokratikleşme"de takılıyordu.
        val boxes = OptionBoxFinder.boxesOf(yaziliBant(haplar, yazilar), 1080, 2400)
        assertEquals(4, boxes.size)
        assertTrue("kutu hapın tamamını kapsamalı", boxes.all { it.height in 140..160 })
        assertEquals(4, OptionBoxFinder.selectRun(boxes).size)
    }

    @Test
    fun `yazili soru karti tek parca olur ve yuksekliginden elenir`() {
        // Kartın yazı satırları da köprüleniyor; kart o zaman tek parça ve
        // haptan çok yüksek (480 piksel) — dizilime hiç karışmıyor.
        val rows = yaziliBant(
            haplar, yazilar,
            kart = 708..1187, kartYazilari = listOf(894..960, 978..1044)
        )
        val boxes = OptionBoxFinder.boxesOf(rows, 1080, 2400)
        assertEquals(4, boxes.size)
        assertTrue(boxes.all { it.top >= 1263 })
    }

    @Test
    fun `haplar arasindaki mor serit kopru kurmaz`() {
        // İki hap arasında hap pikseli yok; yazı kuralı onları birleştiremez.
        val rows = yaziliBant(listOf(1263..1412, 1470..1619), listOf(1326..1350))
        val boxes = OptionBoxFinder.boxesOf(rows, 1080, 2400)
        assertEquals(2, boxes.size)
    }

    @Test
    fun `kenarlari tutmayan satir kopru kurmaz`() {
        // Hapın ortasına kenarları başka yerde olan bir satır (joker şeridi
        // gibi) girerse yazı sayılmaz: iki yarım da kutu olamayacak kadar kısa.
        val rows = ArrayList<OptionBoxFinder.Row>()
        var y = 1200
        while (y < 1500) {
            rows.add(
                when (y) {
                    in 1263..1322 -> row(y, 0.70f, 162, 918)
                    in 1323..1352 -> row(y, 0.36f, 234, 837)
                    in 1353..1412 -> row(y, 0.70f, 162, 918)
                    else -> row(y, 0f, -1, -1)
                }
            )
            y += OptionBoxFinder.ROW_STEP
        }
        assertTrue(OptionBoxFinder.boxesOf(rows, 1080, 2400).isEmpty())
    }

    @Test
    fun `yaziyla biten kutu son hap satirinda kapanir`() {
        // Arkasından hap satırı gelmeyen yazı satırları kutuya katılmıyor;
        // kutunun sınırları yalnızca hap satırlarından geliyor.
        val rows = ArrayList<OptionBoxFinder.Row>()
        var y = 1200
        while (y < 1500) {
            rows.add(
                when (y) {
                    in 1263..1382 -> row(y, 0.70f, 162, 918)
                    in 1383..1412 -> row(y, 0.50f, 162, 918)
                    else -> row(y, 0f, -1, -1)
                }
            )
            y += OptionBoxFinder.ROW_STEP
        }
        val boxes = OptionBoxFinder.boxesOf(rows, 1080, 2400)
        assertEquals(1, boxes.size)
        assertTrue(boxes[0].bottom <= 1386)
    }

    // --- Elle seçilmiş şık bandı (Ekranı ayarla) ---------------------------

    @Test
    fun `yatay tablette haplar ancak bandin genisligine gore bulunur`() {
        // 2560x1600 yatay tablet: oyun ortada dar bir sütunda, haplar
        // x1030-1530 (ekranın %20'si). Ekran genişliğine göre hap satırı
        // sayılmıyor; seçilen bandın (x960-1600) genişliğine göre sayılıyor.
        val rows = ArrayList<OptionBoxFinder.Row>()
        var y = 0
        while (y < 1600) {
            val hap = listOf(760..860, 900..1000, 1040..1140, 1180..1280).any { y in it }
            // Doluluk bandın örnek sütunlarına göre: hap bandın ~%80'i.
            rows.add(if (hap) row(y, 0.78f, 1030, 1530) else row(y, 0f, -1, -1))
            y += OptionBoxFinder.ROW_STEP
        }
        assertTrue(OptionBoxFinder.boxesOf(rows, screenW = 2560, screenH = 1600).isEmpty())
        val boxes = OptionBoxFinder.boxesOf(rows, screenW = 1600 - 960, screenH = 1600)
        assertEquals(4, OptionBoxFinder.selectRun(boxes).size)
    }

    @Test
    fun `satir olcumu bandin disina bakmaz`() {
        // Bandın solunda beyaz bir panel var; ölçüme girmemeli.
        val w = 1000
        val px = IntArray(w) { x ->
            when (x) {
                in 0 until 300 -> 0xFFFFFFFF.toInt()
                in 400 until 900 -> 0xFFF8F8F8.toInt()
                else -> 0xFF482898.toInt()
            }
        }
        val r = OptionBoxFinder.measure(0, px, width = 950, step = 1, from = 350)
        assertEquals(400, r.left)
        assertEquals(899, r.right)
        assertEquals(500f / 600f, r.fill, 0.01f)
    }
}
