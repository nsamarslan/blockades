package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Şıkların piksel imzası.
 *
 * Gerçek günlük: "ve" / "veya" bağlacı, "büyüktür" sorularında şıklar ∨ ∧ >
 * <; ML Kit üçünü de «V» okuyor. Metin aynı olduğu için doğru cevap
 * kaydedilemiyordu. Burada semboller beyaz hap üstüne lacivert çizgilerle
 * çiziliyor: aynı sembol başka boyutta ve yerde aynı imzayı vermeli,
 * farklı semboller açıkça ayrılmalı.
 */
class SikImzasiTest {

    private val BEYAZ = 0xFFF8F8F8.toInt()
    private val LACIVERT = 0xFF2A2060.toInt()

    private enum class Sembol { KUCUKTUR, BUYUKTUR, VEYA, VE }

    /** [w]x[h] beyaz zemine, (ox, oy) köşesinden [boy] büyüklüğünde sembol çizer. */
    private fun ciz(w: Int, h: Int, sembol: Sembol, ox: Int, oy: Int, boy: Int, kalinlik: Int = 3): IntArray {
        val px = IntArray(w * h) { BEYAZ }
        fun nokta(x: Int, y: Int) {
            for (dy in 0 until kalinlik) for (dx in 0 until kalinlik) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) px[yy * w + xx] = LACIVERT
            }
        }
        fun cizgi(x0: Int, y0: Int, x1: Int, y1: Int) {
            val adim = maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).coerceAtLeast(1)
            for (i in 0..adim) nokta(x0 + (x1 - x0) * i / adim, y0 + (y1 - y0) * i / adim)
        }
        val sol = ox
        val sag = ox + boy
        val ust = oy
        val alt = oy + boy
        val ortaX = ox + boy / 2
        val ortaY = oy + boy / 2
        when (sembol) {
            Sembol.KUCUKTUR -> { cizgi(sag, ust, sol, ortaY); cizgi(sol, ortaY, sag, alt) }
            Sembol.BUYUKTUR -> { cizgi(sol, ust, sag, ortaY); cizgi(sag, ortaY, sol, alt) }
            Sembol.VEYA -> { cizgi(sol, ust, ortaX, alt); cizgi(ortaX, alt, sag, ust) }
            Sembol.VE -> { cizgi(sol, alt, ortaX, ust); cizgi(ortaX, ust, sag, alt) }
        }
        return px
    }

    private fun imza(sembol: Sembol, ox: Int = 200, oy: Int = 20, boy: Int = 40, w: Int = 600, h: Int = 80) =
        SikImzasi.hesapla(ciz(w, h, sembol, ox, oy, boy), w, h)

    @Test
    fun `ayni sembol baska yerde ve boyda ayni imza`() {
        val a = imza(Sembol.VEYA)
        val b = imza(Sembol.VEYA, ox = 330, oy = 10, boy = 56, w = 700, h = 90)
        val d = SikImzasi.mesafe(a, b)
        assertNotNull(d)
        assertTrue("mesafe $d", d!! <= 24)
    }

    @Test
    fun `farkli semboller acikca ayrilir`() {
        val hepsi = Sembol.values().map { imza(it) }
        for (i in hepsi.indices) for (j in hepsi.indices) {
            if (i == j) continue
            val d = SikImzasi.mesafe(hepsi[i], hepsi[j])!!
            assertTrue("${Sembol.values()[i]} / ${Sembol.values()[j]}: $d", d >= 60)
        }
    }

    @Test
    fun `ekranda dogru sembol secilir`() {
        // Ekran: A «V»(∨), B «V»(∧), C «V»(>), D «<». Kayıtta doğru cevap ∧.
        val ekran = listOf(
            imza(Sembol.VEYA, ox = 250),
            imza(Sembol.VE, ox = 260, boy = 44),
            imza(Sembol.BUYUKTUR, ox = 240),
            imza(Sembol.KUCUKTUR)
        )
        val kayittaki = imza(Sembol.VE, ox = 180, boy = 38)
        assertEquals(1, SikImzasi.enYakin(kayittaki, listOf(0, 1, 2), ekran))
    }

    @Test
    fun `karar verilemiyorsa tahmin yok`() {
        val veya = imza(Sembol.VEYA)
        // İki aday da aynı sembol: hangisi olduğu bilinemez.
        assertNull(SikImzasi.enYakin(veya, listOf(0, 1), listOf(imza(Sembol.VEYA), imza(Sembol.VEYA, ox = 300))))
        // Adaylardan birinin imzası yok.
        assertNull(SikImzasi.enYakin(veya, listOf(0, 1), listOf(imza(Sembol.VEYA), null)))
        // Hedefin imzası yok.
        assertNull(SikImzasi.enYakin(null, listOf(0, 1), listOf(veya, imza(Sembol.VE))))
    }

    @Test
    fun `yazisiz kutunun imzasi yok`() {
        assertNull(SikImzasi.hesapla(IntArray(600 * 80) { BEYAZ }, 600, 80))
        assertNull(SikImzasi.mesafe("ab", "abc"))
    }

    @Test
    fun `renkli hap yazi sayilmaz`() {
        // Kırmızı hap (248,104,104) yazı değil; üstündeki lacivert yazı hâlâ yazı.
        val w = 600
        val h = 80
        val kirmizi = 0xFFF86868.toInt()
        val px = ciz(w, h, Sembol.VE, 200, 20, 40).map { if (it == BEYAZ) kirmizi else it }.toIntArray()
        val d = SikImzasi.mesafe(SikImzasi.hesapla(px, w, h), imza(Sembol.VE))
        assertEquals(0, d)
    }
}
