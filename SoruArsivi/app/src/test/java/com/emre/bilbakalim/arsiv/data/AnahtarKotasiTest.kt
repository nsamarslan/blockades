package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnahtarKotasiTest {

    @Test
    fun `istekler sayilir ve gun degisince sifirlanir`() {
        var d = AnahtarKotasi.guncelle(null, "2026-10-05", 1000, 200, null, 999, 1000)
        d = AnahtarKotasi.guncelle(d, "2026-10-05", 2000, 429, "kota doldu", null, null)
        assertEquals(2, d.istek)
        assertEquals(1, d.basarili)
        assertEquals(1, d.kotaHatasi)
        assertEquals(999, d.kalanIstek)
        assertEquals(1000, d.istekSiniri)
        assertEquals("kota doldu", d.sonHata)
        assertEquals(2000, d.sonHataAt)
        assertEquals(1000, d.sonBasariAt)

        val yarin = AnahtarKotasi.guncelle(d, "2026-10-06", 3000, 200, null, null, null)
        assertEquals(1, yarin.istek)
        assertEquals(0, yarin.kotaHatasi)
        assertNull(yarin.kalanIstek)
    }

    @Test
    fun `json gidip gelir`() {
        val d = AnahtarKotasi.guncelle(null, "2026-10-05", 1000, 401, "anahtar reddedildi", null, null)
        assertEquals(d, AnahtarKotasi.oku(AnahtarKotasi.yaz(d)))
        assertNull(AnahtarKotasi.oku("bozuk"))
    }

    @Test
    fun `ozet anahtari icermez`() {
        val o = AnahtarKotasi.ozet("Groq", "gsk_gizli")
        assertEquals(false, o.contains("gsk_gizli"))
        assertEquals(o, AnahtarKotasi.ozet("Groq", " gsk_gizli "))
    }
}
