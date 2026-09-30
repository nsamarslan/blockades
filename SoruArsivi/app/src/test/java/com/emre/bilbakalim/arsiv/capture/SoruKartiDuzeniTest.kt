package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.capture.SoruKartiDuzeni.HapRengi
import com.emre.bilbakalim.arsiv.capture.SoruKartiDuzeni.Kaynak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dışa aktarılan soru görselindeki "doğru şık" işaretinin kararları.
 *
 * Renkler tarama günlüğünde ölçülenler; ekran düzeni telefondaki oyun
 * ekranının 820 piksele küçültülmüş hâli (kaydedilen görüntü bu boyda).
 */
class SoruKartiDuzeniTest {

    private val mor = rgb(72, 40, 152)
    private val beyaz = rgb(248, 248, 248)
    private val yazi = rgb(40, 45, 110)
    private val yesil = rgb(120, 240, 170)
    private val kirmizi = rgb(248, 104, 104)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private class Ekran(val w: Int, val h: Int, zemin: Int) {
        val px = IntArray(w * h) { zemin }
        fun boya(l: Int, t: Int, r: Int, b: Int, c: Int) {
            for (y in t until b) for (x in l until r) px[y * w + x] = c
        }
        fun satir(y: Int) = px.copyOfRange(y * w, y * w + w)
        fun parca(l: Int, t: Int, r: Int, b: Int) =
            IntArray((r - l) * (b - t)) { i -> px[(t + i / (r - l)) * w + l + i % (r - l)] }
    }

    /** 820x1822: kart 520..1000, dört hap 1100'den 140 aralıkla. */
    private fun oyunEkrani(renkler: List<Int>): Pair<Ekran, List<OptionBoxFinder.Box>> {
        val e = Ekran(820, 1822, mor)
        e.boya(70, 520, 750, 1000, beyaz)
        // Kartın içindeki soru yazısı: koyu satırlar.
        for (s in 0 until 4) e.boya(110, 580 + s * 90, 700, 610 + s * 90, yazi)
        val kutular = (0 until 4).map { i ->
            OptionBoxFinder.Box(140, 1100 + i * 140, 680, 1200 + i * 140)
        }
        kutular.forEachIndexed { i, k ->
            e.boya(k.left, k.top, k.right, k.bottom, renkler[i])
            e.boya(390, k.top + 35, 430, k.bottom - 35, yazi)
        }
        return e to kutular
    }

    private fun renkler(e: Ekran, kutular: List<OptionBoxFinder.Box>) = kutular.map { k ->
        SoruKartiDuzeni.zeminRengi(
            e.parca(
                (k.left + k.width * 0.12f).toInt(), (k.top + k.height * 0.2f).toInt(),
                (k.right - k.width * 0.12f).toInt(), (k.bottom - k.height * 0.2f).toInt()
            )
        )
    }

    @Test
    fun `olculen hap renkleri dogru siniflanir`() {
        assertEquals(HapRengi.BEYAZ, SoruKartiDuzeni.hapRengi(248, 248, 248))
        assertEquals(HapRengi.YESIL, SoruKartiDuzeni.hapRengi(136, 248, 136))
        assertEquals(HapRengi.YESIL, SoruKartiDuzeni.hapRengi(88, 248, 72))
        assertEquals(HapRengi.YESIL, SoruKartiDuzeni.hapRengi(120, 240, 170))
        assertEquals(HapRengi.KIRMIZI, SoruKartiDuzeni.hapRengi(248, 104, 104))
        assertEquals(HapRengi.SECILI, SoruKartiDuzeni.hapRengi(248, 216, 88))
        // Turkuaz yeşil sayılmamalı: seçilmiş ama karar açılmamış.
        assertTrue(SoruKartiDuzeni.hapRengi(110, 240, 220) != HapRengi.YESIL)
    }

    @Test
    fun `secilen kirmizi dogrusu yesil ise ok yesile gider`() {
        val (e, kutular) = oyunEkrani(listOf(beyaz, kirmizi, beyaz, yesil))
        val r = renkler(e, kutular)
        assertEquals(listOf(HapRengi.BEYAZ, HapRengi.KIRMIZI, HapRengi.BEYAZ, HapRengi.YESIL), r)
        // Arşiv başka bir şey dese de oyunun yeşili kazanıyor; metin de gerekmiyor.
        val karar = SoruKartiDuzeni.dogruKutu(r, listOf("20", "25", "8", "10"), emptyList(), 0, null, emptyList())
        assertEquals(SoruKartiDuzeni.Karar(3, Kaynak.RENK), karar)
    }

    @Test
    fun `renk yoksa arsivdeki cevap metinle bulunur`() {
        val (e, kutular) = oyunEkrani(List(4) { beyaz })
        val r = renkler(e, kutular)
        // Kayıtta sıra başka, ekranda başka: metin eşleşiyor.
        val karar = SoruKartiDuzeni.dogruKutu(
            r, listOf("20", "25", "8", "10"), emptyList(), 0,
            listOf("25", "8", "20", "10"), List(4) { null }
        )
        assertEquals(SoruKartiDuzeni.Karar(2, Kaynak.METIN), karar)
        // Metin yok, renk yok: işaret yok (yanlış yeri göstermektense).
        assertNull(SoruKartiDuzeni.dogruKutu(r, listOf("20", "25", "8", "10"), emptyList(), 0, null, emptyList()))
        // Cevap arşivde yok.
        assertNull(SoruKartiDuzeni.dogruKutu(r, listOf("20", "25", "8", "10"), emptyList(), null,
            listOf("25", "8", "20", "10"), List(4) { null }))
        // Ekranda doğru metin iki kez: belirsiz.
        assertNull(SoruKartiDuzeni.dogruKutu(r, listOf("Ay", "Mars", "Venüs", "Dünya"), emptyList(), 0,
            listOf("Ay", "Ay", "Venüs", "Dünya"), List(4) { null }))
    }

    @Test
    fun `soru karti bulunur ve kirpma soruyu ve siklari kapsar`() {
        val (e, kutular) = oyunEkrani(List(4) { beyaz })
        val pw = kutular.maxOf { it.width }
        val x0 = (kutular.minOf { it.left } - pw / 4).coerceAtLeast(0)
        val x1 = (kutular.maxOf { it.right } + pw / 4).coerceAtMost(e.w)
        val adim = ((x1 - x0) / 120).coerceAtLeast(1)
        val satirlar = (0 until kutular.first().top step OptionBoxFinder.ROW_STEP)
            .map { y -> OptionBoxFinder.measure(y, e.satir(y), x1, adim, x0) }
        val kart = SoruKartiDuzeni.kartBul(satirlar, kutular.first().top, x1 - x0, e.h)
        assertNotNull(kart)
        assertTrue("kart üstü ${kart!!.ust}", kart.ust in 515..530)
        val k = SoruKartiDuzeni.kirpma(kutular, kart, e.w, e.h)
        assertTrue("sorunun üstü dahil", k.ust < 520)
        assertTrue("son şık dahil", k.alt >= kutular.last().bottom)
        assertTrue("yanlar kart kadar", k.sol <= 70 && k.sag >= 750)
    }

    @Test
    fun `kart bulunamazsa ilk hapin epey ustunden tam genislik`() {
        val kutular = listOf(OptionBoxFinder.Box(140, 1100, 680, 1200), OptionBoxFinder.Box(140, 1240, 680, 1340))
        val k = SoruKartiDuzeni.kirpma(kutular, null, 820, 1822)
        assertEquals(0, k.sol)
        assertEquals(820, k.sag)
        assertTrue(k.ust < 1100 - 700)
    }

    @Test
    fun `yan dosya gidip gelir ve bozugu reddedilir`() {
        val y = SoruKartiDuzeni.SikYerlesimi(
            listOf("20", "(okunamadı)"),
            listOf(OptionBoxFinder.Box(1, 2, 300, 60), OptionBoxFinder.Box(1, 80, 300, 140)), "kutu"
        )
        val geri = SoruKartiDuzeni.SikYerlesimi.oku(y.json())
        assertEquals(y, geri)
        assertTrue(geri!!.gecerli(820, 1822))
        assertTrue(!geri.gecerli(200, 1822))
        assertNull(SoruKartiDuzeni.SikYerlesimi.oku("bozuk"))
    }
}
