package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.capture.UzakGeometri.Kutu
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uzak modun konum hesapları.
 *
 * Örnek düzen: 1080x2400 telefonda dikey izlenen bir yayın; oyun ekranın
 * ortasında, 400 piksel genişliğinde bir sütun (x 340-740).
 */
class UzakGeometriTest {

    private val w = 1080
    private val h = 2400
    private val soru = EkranBolgesi(340f / w, 900f / h, 740f / w, 1100f / h)
    private val sik = EkranBolgesi(360f / w, 1150f / h, 720f / w, 1550f / h)

    @Test
    fun `cerceve iki bolgeyi payla kapsar`() {
        val c = UzakGeometri.cerceve(soru, sik, w, h)
        // Birleşim 340..740 x 900..1550 (650 yüksek), pay %6 = 39.
        assertEquals(Kutu(301, 861, 779, 1589), c)
        // Ekranın kenarında kırpılır.
        val kenar = UzakGeometri.cerceve(EkranBolgesi(0f, 0f, 0.5f, 0.2f), EkranBolgesi(0f, 0.2f, 0.5f, 0.5f), w, h)
        assertEquals(0, kenar.sol)
        assertEquals(0, kenar.ust)
    }

    @Test
    fun `goreli bolge cercevenin icinde`() {
        val c = UzakGeometri.cerceve(soru, sik, w, h)
        val g = UzakGeometri.goreli(sik, c, w, h)
        assertEquals((360f - 301f) / c.w, g.sol, 0.002f)
        assertEquals((1150f - 861f) / c.h, g.ust, 0.002f)
        assertTrue(g.gecerli)
        // Seçilen bölgenin üstünde ayrıştırıcının "durum çubuğu" sınırından
        // (%4,5) fazla pay kalıyor: sorunun ilk satırı atılmasın.
        val gs = UzakGeometri.goreli(soru, c, w, h)
        assertTrue(gs.ust > 0.045f)
    }

    @Test
    fun `kucuk yayin buyutulur`() {
        assertEquals(2, UzakGeometri.olcek(478))
        assertEquals(3, UzakGeometri.olcek(200))
        assertEquals(1, UzakGeometri.olcek(900))
        assertEquals(1, UzakGeometri.olcek(0))
    }

    @Test
    fun `ok cercevenin disinda solda saga bakar`() {
        val c = UzakGeometri.cerceve(soru, sik, w, h)
        val hedef = Kutu(360, 1250, 720, 1330)
        val yer = UzakGeometri.isaretYeri(hedef, c, w, 120, 60)
        assertNotNull(yer)
        assertTrue(yer!!.sagaBakar)
        assertEquals(c.sol - 120, yer.x)
        assertEquals(1290 - 30, yer.y)
        assertFalse(Kutu(yer.x, yer.y, yer.x + yer.w, yer.y + yer.h).kesisir(c))
    }

    @Test
    fun `solda yer yoksa sagda sola bakar, ikisinde de yoksa ok yok`() {
        val sola = Kutu(20, 800, 900, 1600)
        val yer = UzakGeometri.isaretYeri(Kutu(40, 1000, 880, 1080), sola, w, 120, 60)
        assertNotNull(yer)
        assertFalse(yer!!.sagaBakar)
        assertEquals(900, yer.x)
        assertNull(UzakGeometri.isaretYeri(Kutu(40, 1000, 1040, 1080), Kutu(20, 800, 1060, 1600), w, 120, 60))
    }

    @Test
    fun `dugme cerceveye birakilirsa en yakin kenara itilir`() {
        val c = Kutu(300, 860, 780, 1590)
        // Sol kenara yakın bırakıldı: sola itilir.
        assertEquals(100 to 1000, UzakGeometri.disariIt(Kutu(320, 1000, 520, 1080), c, w, h))
        // Üst kenara yakın: yukarı.
        assertEquals(500 to 780, UzakGeometri.disariIt(Kutu(500, 870, 700, 950), c, w, h))
        // Dışarıdaysa yerinde kalır.
        assertEquals(10 to 10, UzakGeometri.disariIt(Kutu(10, 10, 200, 80), c, w, h))
    }

    @Test
    fun `dugme metinleri`() {
        assertEquals("✓ C · Deva", UzakIzleyici.gorunum(UzakIzleyici.Durum.Bilinen(2, "Deva")).first)
        assertEquals(UzakKatman.Ton.KIRMIZI, UzakIzleyici.gorunum(UzakIzleyici.Durum.ArsivdeYok).second)
        assertEquals(UzakKatman.Ton.TURUNCU, UzakIzleyici.gorunum(UzakIzleyici.Durum.CevapYok).second)
        // Yapay zekâ tahmini bilinen cevaptan ayrı renkte, kaynağıyla.
        val tahmin = UzakIzleyici.gorunum(UzakIzleyici.Durum.Tahmin(1, "Ankara", "Groq 2"))
        assertEquals("🤖 B · Ankara (Groq 2)", tahmin.first)
        assertEquals(UzakKatman.Ton.MOR, tahmin.second)
        val emin = UzakIzleyici.gorunum(UzakIzleyici.Durum.Tahmin(1, "Ankara", "Groq 2", 90))
        assertEquals("🤖 B · Ankara (Groq 2, %90)", emin.first)
        val degil = UzakIzleyici.gorunum(UzakIzleyici.Durum.Tahmin(1, "Ankara", "Groq 2", 40, eminDegil = true))
        assertEquals("🤖? B · Ankara (Groq 2, %40)", degil.first)
    }
}
