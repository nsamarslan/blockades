package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SikEslemeTest {
    private val ve = "0".repeat(64)
    private val veya = "f".repeat(64)
    private val buyuktur = "a".repeat(64)
    private val kucuktur = "5".repeat(64)

    @Test
    fun `okunamayan birden cok sik yer degistirince imzayla bulunur`() {
        val options = listOf("(okunamadı)", "<", "(okunamadı)", ">")
        val sigs = listOf(veya, kucuktur, ve, buyuktur)
        assertEquals(2, SikEsleme.bul("(okunamadı)", ve, options, sigs))
        assertEquals(0, SikEsleme.bul("(okunamadı)", veya, options, sigs))
        assertNull(SikEsleme.bul("(okunamadı)", null, options, sigs))
    }

    @Test
    fun `benzersiz gorunen V yanlis goruntuyle kabul edilmez`() {
        val options = listOf("V", "<", ">", "8")
        val sigs = listOf(veya, kucuktur, buyuktur, ve)
        assertNull(SikEsleme.bul("V", ve, options, sigs))
        assertEquals(0, SikEsleme.bul("V", veya, options, sigs))
        assertNull(SikEsleme.bul("V", null, options, sigs))
    }

    @Test
    fun `imzasiz veya ayni iki imzada tahmin edilmez`() {
        val options = listOf("(okunamadı)", "(okunamadı)", "<", ">")
        assertNull(SikEsleme.bul("(okunamadı)", ve, options, listOf(null, veya, kucuktur, buyuktur)))
        assertNull(SikEsleme.bul("(okunamadı)", ve, options, listOf(ve, ve, kucuktur, buyuktur)))
    }
    @Test
    fun `ayni metinli ama farkli okunamayan semboller birlestirilmez`() {
        val options = listOf("(okunamadı)", "<", "(okunamadı)", ">")
        val shuffled = listOf("(okunamadı)", ">", "<", "(okunamadı)")
        val oldSigs = listOf(ve, kucuktur, veya, buyuktur)
        assertEquals(true, SikEsleme.okunamayanlarAyni(
            options, oldSigs, shuffled, listOf(veya, buyuktur, kucuktur, ve)
        ))
        assertEquals(false, SikEsleme.okunamayanlarAyni(
            options, oldSigs, shuffled, listOf(ve, buyuktur, kucuktur, ve)
        ))
        assertEquals(false, SikEsleme.okunamayanlarAyni(
            options, oldSigs, shuffled, listOf(null, buyuktur, kucuktur, veya)
        ))
    }

    @Test
    fun `imzasiz eski kayit okunamayan sik yuzunden reddedilmez`() {
        // 3.9 öncesi: «(okunamadı)» şıkkı var, imza hiç yok. Doğrulanamıyor;
        // eskisi gibi metinle devam, imzalar bu karşılaşmada yazılır.
        val eski = listOf("(okunamadı)", "<", ">", "8")
        val ekran = listOf(">", "(okunamadı)", "8", "<")
        val ekranImza = listOf(buyuktur, ve, veya, kucuktur)
        assertEquals(true, SikEsleme.okunamayanlarAyni(eski, emptyList(), ekran, ekranImza))
        assertEquals(true, SikEsleme.okunamayanlarAyni(eski, listOf(null, null, null, null), ekran, ekranImza))
    }

    @Test
    fun `okunamadi bu kez V okununca dort imza eslesirse ayni set`() {
        val eski = listOf("(okunamadı)", "<", ">", "8")
        val eskiImza = listOf(ve, kucuktur, buyuktur, veya)
        // Aynı set, karışık sırada; OCR ∧'yi bu kez «V» okudu.
        assertEquals(true, SikEsleme.okunamayanlarAyni(
            eski, eskiImza, listOf("<", "V", "8", ">"), listOf(kucuktur, ve, veya, buyuktur)
        ))
        // Tersi: kayıtta okunmuş bir şık bu kez okunamadı.
        assertEquals(true, SikEsleme.okunamayanlarAyni(
            listOf("V", "<", ">", "8"), eskiImza,
            listOf("<", "(okunamadı)", "8", ">"), listOf(kucuktur, ve, veya, buyuktur)
        ))
        // Görüntü başka: ∧ yerine ∨ var (veya iki kez).
        assertEquals(false, SikEsleme.okunamayanlarAyni(
            eski, eskiImza, listOf("<", "V", "8", ">"), listOf(kucuktur, veya, veya, buyuktur)
        ))
    }

    @Test
    fun `imzasi alinamayan kayitta kisa cevap metinle bulunur`() {
        val ekran = listOf("15", "12", "14", "13")
        val imzalar = listOf(ve, veya, buyuktur, kucuktur)
        // Kayıtta imza yok (eski ya da elle düzeltilmiş kayıt).
        assertEquals(1, SikEsleme.bul("12", null, ekran, imzalar))
        assertEquals(1, SikEsleme.bul("1/6", null, listOf("1/3", "1/6", "5/6", "2/4"), imzalar))
        assertEquals(2, SikEsleme.bul("-4", null, listOf("16", "-16", "-4", "4"), imzalar))
        assertEquals(0, SikEsleme.bul("<", null, listOf("<", ">", "V", "8"), imzalar))
        // Ekranın imzası yok (cevap kaydı, kayıt imzalı).
        assertEquals(1, SikEsleme.bul("12", veya, ekran, listOf(ve, null, buyuktur, kucuktur)))
        // Tek harf/rakam OCR'ın simgeleri karıştırdığı yer: imzasız tahmin yok.
        assertNull(SikEsleme.bul("8", null, listOf("8", "<", ">", "V"), imzalar))
        assertNull(SikEsleme.bul("V", veya, listOf("V", "<", ">", "8"), listOf(null, veya, kucuktur, buyuktur)))
    }

    @Test
    fun `iki tarafta imza varsa kisa cevap imzayla dogrulanir`() {
        val ekran = listOf("15", "12", "14", "13")
        val imzalar = listOf(ve, veya, buyuktur, kucuktur)
        assertEquals(1, SikEsleme.bul("12", veya, ekran, imzalar))
        assertNull(SikEsleme.bul("12", ve, ekran, imzalar))
    }

    @Test
    fun `OCR metni degisse de dort gorsel sik birebir eslesir`() {
        val stored = listOf(ve, veya, buyuktur, kucuktur)
        val shuffled = listOf(kucuktur, ve, buyuktur, veya)
        assertEquals(listOf(1, 3, 2, 0), SikImzasi.eslesmeSirasi(stored, shuffled))
        assertNull(SikImzasi.eslesmeSirasi(stored, listOf(ve, ve, buyuktur, veya)))
        assertNull(SikImzasi.eslesmeSirasi(stored, listOf(ve, null, buyuktur, kucuktur)))
    }

}
