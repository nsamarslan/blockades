package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YapayZekaIstatistikTest {

    @Test
    fun `saglayici kategori ve guven ayri sayilir`() {
        var v = emptyMap<String, YapayZekaIstatistik.Saglayici>()
        v = YapayZekaIstatistik.eklenmis(v, "Groq", "İngilizce Lügat", 95, true)
        v = YapayZekaIstatistik.eklenmis(v, "Groq", "İngilizce Lügat", 40, false)
        v = YapayZekaIstatistik.eklenmis(v, "Gemini", null, null, true)

        val groq = v.getValue("Groq")
        assertEquals(YapayZekaIstatistik.Sayac(1, 2), groq.toplam)
        assertEquals(50, groq.toplam.yuzde)
        assertEquals(YapayZekaIstatistik.Sayac(1, 2), groq.kategori["İngilizce Lügat"])
        assertEquals(YapayZekaIstatistik.Sayac(1, 1), groq.guven["%90-100"])
        assertEquals(YapayZekaIstatistik.Sayac(0, 1), groq.guven["%0-49"])
        val gemini = v.getValue("Gemini")
        assertEquals(YapayZekaIstatistik.Sayac(1, 1), gemini.kategori[YapayZekaIstatistik.ETIKETSIZ])
        assertEquals(YapayZekaIstatistik.Sayac(1, 1), gemini.guven["güven yok"])
    }

    @Test
    fun `json gidip gelince ayni kalir`() {
        var v = emptyMap<String, YapayZekaIstatistik.Saglayici>()
        v = YapayZekaIstatistik.eklenmis(v, "Groq", "Tarih", 80, true)
        v = YapayZekaIstatistik.eklenmis(v, "Gemini", "Tarih", 60, false)
        assertEquals(v, YapayZekaIstatistik.oku(YapayZekaIstatistik.yaz(v)))
        assertTrue(YapayZekaIstatistik.oku("bozuk").isEmpty())
        assertTrue(YapayZekaIstatistik.oku(null).isEmpty())
        assertNull(YapayZekaIstatistik.Sayac().yuzde)
    }

    @Test
    fun `guven araliklari`() {
        assertEquals("%90-100", YapayZekaIstatistik.guvenAraligi(90))
        assertEquals("%70-89", YapayZekaIstatistik.guvenAraligi(89))
        assertEquals("%50-69", YapayZekaIstatistik.guvenAraligi(50))
        assertEquals("%0-49", YapayZekaIstatistik.guvenAraligi(0))
        assertEquals("güven yok", YapayZekaIstatistik.guvenAraligi(null))
    }
}
