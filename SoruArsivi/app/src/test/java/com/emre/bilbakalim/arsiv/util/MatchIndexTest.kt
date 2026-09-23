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

    // --- Yalnızca Türkçe harfle ayrılan şıklar ----------------------------
    //
    // Gerçek soru: "Var olmak için kendisinden başka hiçbir şeye ihtiyaç
    // duymayan şey olarak tanımlanan felsefi kavram hangisidir?" Şıklar
    // "Öz / Toz / Oz / Töz", cevap "Töz". Eşleştirme harfleri katladığı
    // (ö→o) ve ilk eşleşeni döndürdüğü için bot "Toz"a basıyordu.

    private val tozlar = listOf("Öz", "Toz", "Oz", "Töz")

    @Test
    fun `yalnizca turkce harfle ayrilan siklar karismaz`() {
        assertEquals(3, TurkishText.matchIndex(tozlar, "Töz"))
        assertEquals(1, TurkishText.matchIndex(tozlar, "Toz"))
        assertEquals(0, TurkishText.matchIndex(tozlar, "Öz"))
        assertEquals(2, TurkishText.matchIndex(tozlar, "Oz"))
    }

    @Test
    fun `noktalama farkinda da turkce harf korunur`() {
        assertEquals(3, TurkishText.matchIndex(tozlar, "Töz."))
        assertEquals(3, TurkishText.matchIndex(tozlar, " töz "))
    }

    @Test
    fun `harfi okunamamis metin iki sikka birden uyuyorsa tahmin edilmez`() {
        // "Töz"ün noktaları düşmüş ve ekranda "Töz" yok: "toz" hem "Toz"a
        // hem "Töz"e katlanmış hâliyle uyuyor ama birebir yalnızca "Toz"a.
        // Ekranda iki "Toz" varsa hangisi olduğu bilinemez.
        assertNull(TurkishText.matchIndex(listOf("Toz", "Toz", "Oz", "Öz"), "Toz"))
        // Birebir tutmayan ve iki şıkka katlanarak uyan metin: belirsiz.
        assertNull(TurkishText.matchIndex(listOf("Töz", "Toz", "Oz", "Öz"), "Tôz"))
    }
}
