package com.emre.bilbakalim.arsiv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Arşivdeki doğru cevabın ekrandaki şıklar arasında bulunması.
 *
 * Bu fonksiyon iki yönde çalışıyor: bot hangi şıkka basacağına buradan
 * karar veriyor, karar açılınca da cevabın kayıttaki hangi şıkka
 * yazılacağı buradan bulunuyor. Yanılırsa hata kendini besliyor.
 */
class MatchIndexTest {

    @Test
    fun `birebir metin bulunur, sira fark etmez`() {
        assertEquals(2, TurkishText.matchIndex(listOf("Köpek", "Fare", "Ayı", "Maymun"), "Ayı"))
    }

    @Test
    fun `ocr harf hatasi affedilir`() {
        assertEquals(1, TurkishText.matchIndex(listOf("Kara meşe", "Beyaz cüce", "Kirmızı göz", "Mavi ladin"), "Beyaz cüçe"))
    }

    @Test
    fun `simgeyle ayrilan siklar karistirilmaz`() {
        // normalizeKey '<' ve '>' işaretini siliyor; eskiden ilk bulunan
        // kazanıyor, "karlı satış" sorusuna hep '<' yazılıyordu.
        val opts = listOf(
            "Satış fiyatı < Maliyet fiyatı", "Satış fiyatı > Maliyet fiyatı",
            "Satış fiyatı = Maliyet fiyatı", "Satış fiyatı ≤ Maliyet fiyatı"
        )
        assertEquals(1, TurkishText.matchIndex(opts, "Satış fiyatı > Maliyet fiyatı"))
        assertEquals(0, TurkishText.matchIndex(opts, "Satış fiyatı < Maliyet fiyatı"))
    }

    @Test
    fun `belirsizlikte null doner`() {
        // Simge farkı OCR'da kaybolduysa hangisi olduğu bilinemez; yanlış
        // şıkka basmaktansa rastgeleye düşmek yeğ.
        val opts = listOf("Satış fiyatı < Maliyet fiyatı", "Satış fiyatı > Maliyet fiyatı", "Eşit", "Hiçbiri")
        assertNull(TurkishText.matchIndex(opts, "Satış fiyatı  Maliyet fiyatı"))
    }

    @Test
    fun `tek haneli sayi siklari birbirine karismaz`() {
        assertEquals(1, TurkishText.matchIndex(listOf("65", "1", "16", "165"), "1"))
        assertEquals(3, TurkishText.matchIndex(listOf("65", "1", "16", "165"), "165"))
    }

    @Test
    fun `hic benzemeyen metin null`() {
        assertNull(TurkishText.matchIndex(listOf("Mars", "Venüs", "Jüpiter", "Satürn"), "Plüton"))
    }
}
