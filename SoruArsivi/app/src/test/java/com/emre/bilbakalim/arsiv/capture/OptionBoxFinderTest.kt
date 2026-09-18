package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Şık kutularının parlaklık profilinden çıkarılması.
 *
 * Değerler gerçek bir ekrandan: 1080x2400, şık şeridi %45–%90 arası
 * (y 1080–2160), dört kutu ~200 piksel yüksekliğinde ve aralarında koyu
 * zemin var. Şeridin üst ucuna ise soru kartının beyaz alt kenarı taşıyor —
 * onu kutu sanmak, dört yerine beş şık okumak demekti.
 */
class OptionBoxFinderTest {

    /** Gerçek ölçülerin örnek adımına (4 piksel) bölünmüş hâli. */
    private val minRun = 13
    private val maxRun = 108

    private fun profil(vararg araliklar: IntRange): BooleanArray {
        val a = BooleanArray(270)
        araliklar.forEach { r -> for (i in r) a[i] = true }
        return a
    }

    @Test
    fun `dort kutu bulunur`() {
        // Kutular: 200 piksel ≈ 50 örnek, aralarında ~4 örnek boşluk.
        val bright = profil(80..129, 134..183, 188..237, 242..265)
        val runs = OptionBoxFinder.runsOf(bright, minRun, maxRun, want = 4)
        assertEquals(4, runs.size)
        assertEquals(80, runs.first().first)
    }

    @Test
    fun `seride tasan soru kartinin alt kenari kutu sayilmaz`() {
        // 0'dan başlayan aralık kırpılmış demektir: gerçek bir şık kutusunun
        // altı da üstü de koyu zeminle çevrilidir.
        val bright = profil(0..39, 80..129, 134..183, 188..237, 242..265)
        val runs = OptionBoxFinder.runsOf(bright, minRun, maxRun, want = 4)
        assertEquals(4, runs.size)
        assertTrue("kırpılmış aralık atılmalı", runs.none { it.first == 0 })
    }

    @Test
    fun `seridin alt ucuna yapisik aralik da atilir`() {
        val bright = profil(80..129, 134..183, 188..237, 250..269)
        val runs = OptionBoxFinder.runsOf(bright, minRun, maxRun, want = 4)
        assertEquals(3, runs.size)
    }

    @Test
    fun `cok kisa ve cok uzun araliklar elenir`() {
        // 3 örnek: joker rozeti. 150 örnek: şeridi kaplayan tek beyaz alan.
        val bright = profil(10..12, 20..169, 180..229)
        val runs = OptionBoxFinder.runsOf(bright, minRun, maxRun, want = 4)
        assertEquals(1, runs.size)
        assertEquals(180..229, runs.single())
    }

    @Test
    fun `fazla aday varsa yuksekligi ortancaya yakin olanlar kalir`() {
        // Dördü aynı boyda (50), biri araya karışmış ufak bir rozet (15).
        val bright = profil(20..69, 75..124, 130..179, 185..199, 205..254)
        val runs = OptionBoxFinder.runsOf(bright, minRun, maxRun, want = 4)
        assertEquals(4, runs.size)
        assertTrue("rozet elenmeli", runs.none { it.first == 185 })
        // Sıra bozulmamalı: kutular yukarıdan aşağıya.
        assertEquals(runs, runs.sortedBy { it.first })
    }

    @Test
    fun `bos profil bos liste dondurur`() {
        assertTrue(OptionBoxFinder.runsOf(BooleanArray(270), minRun, maxRun, 4).isEmpty())
        assertTrue(OptionBoxFinder.runsOf(BooleanArray(2), minRun, maxRun, 4).isEmpty())
    }
}
