package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ekranı ayarla" ile seçilen bölgelerin saklanması ve çevrilmesi. */
class EkranBolgesiTest {

    @Test
    fun `yazilip okunan bolge ayni`() {
        val b = EkranBolgesi(0.06f, 0.22f, 0.94f, 0.50f)
        assertEquals(b, EkranBolgesi.oku(b.metin()))
    }

    @Test
    fun `bozuk ya da cizgi gibi bolge reddedilir`() {
        assertNull(EkranBolgesi.oku(null))
        assertNull(EkranBolgesi.oku("0.1,0.2,0.3"))
        assertNull(EkranBolgesi.oku("a,b,c,d"))
        // Parmağın kayması: çok dar.
        assertNull(EkranBolgesi.oku("0.5,0.2,0.52,0.6"))
        assertFalse(EkranBolgesi(0.5f, 0.5f, 0.9f, 0.51f).gecerli)
    }

    @Test
    fun `iki noktadan hangi yone cizilirse cizilsin ayni bolge`() {
        val a = EkranBolgesi.ikiNoktadan(0.9f, 0.8f, 0.1f, 0.5f)
        assertEquals(EkranBolgesi(0.1f, 0.5f, 0.9f, 0.8f), a)
        // Kare dışına taşan sürükleme kırpılıyor.
        val b = EkranBolgesi.ikiNoktadan(-0.2f, 0.4f, 1.3f, 0.9f)
        assertEquals(EkranBolgesi(0f, 0.4f, 1f, 0.9f), b)
        assertTrue(b.gecerli)
    }

    @Test
    fun `piksel araliklari payla`() {
        val b = EkranBolgesi(0.1f, 0.5f, 0.9f, 0.8f)
        assertEquals(108..972, b.yatay(1080))
        assertEquals(1200..1920, b.dikey(2400))
        // Pay ekranın dışına taşmıyor.
        assertEquals(0..1080, EkranBolgesi(0f, 0f, 1f, 1f).yatay(1080, 0.02f))
    }
}
