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
    fun `OCR metni degisse de dort gorsel sik birebir eslesir`() {
        val stored = listOf(ve, veya, buyuktur, kucuktur)
        val shuffled = listOf(kucuktur, ve, buyuktur, veya)
        assertEquals(listOf(1, 3, 2, 0), SikImzasi.eslesmeSirasi(stored, shuffled))
        assertNull(SikImzasi.eslesmeSirasi(stored, listOf(ve, ve, buyuktur, veya)))
        assertNull(SikImzasi.eslesmeSirasi(stored, listOf(ve, null, buyuktur, kucuktur)))
    }

}
