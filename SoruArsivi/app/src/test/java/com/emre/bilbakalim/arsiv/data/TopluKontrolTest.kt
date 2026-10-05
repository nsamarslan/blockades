package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.data.TopluKontrol.Karar
import com.emre.bilbakalim.arsiv.util.Importers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopluKontrolTest {

    @Test
    fun `ikisi ayni derse cevapsiz soruya yazilir`() {
        assertEquals(Karar.KAYDET, TopluKontrol.kararVer(null, null, false, 2, 2))
    }

    @Test
    fun `kayitla ayniysa dogrulanir`() {
        assertEquals(Karar.AYNI, TopluKontrol.kararVer(1, "renk", false, 1, 1))
    }

    @Test
    fun `oyunda gorulmemis yanlis cevap duzeltilir`() {
        assertEquals(Karar.DUZELT, TopluKontrol.kararVer(0, Importers.ANSWER_SOURCE, false, 2, 2))
        assertEquals(Karar.DUZELT, TopluKontrol.kararVer(0, Repo.YAPAY_ZEKA, false, 2, 2))
    }

    @Test
    fun `oyunda gorulen ya da elle secilen cevap kendiliginden degismez`() {
        assertTrue(TopluKontrol.kararVer(0, "renk (kesin)", false, 2, 2) is Karar.Listele)
        assertTrue(TopluKontrol.kararVer(0, Importers.ANSWER_SOURCE, true, 2, 2) is Karar.Listele)
    }

    @Test
    fun `farkli derlerse karar sende`() {
        assertTrue(TopluKontrol.kararVer(null, null, false, 1, 2) is Karar.Listele)
        assertTrue(TopluKontrol.kararVer(null, null, false, null, 2) is Karar.Listele)
        assertTrue(TopluKontrol.kararVer(0, Importers.ANSWER_SOURCE, false, 0, 2) is Karar.Listele)
        // Oyunda görülmüş cevabı biri destekliyorsa liste dolmasın.
        assertEquals(Karar.AYNI, TopluKontrol.kararVer(0, "renk", false, 0, 2))
    }

    @Test
    fun `bekleyen liste dosyaya yazilip okunur`() {
        val l = listOf(
            TopluKontrol.Bekleyen(7, "Related", listOf("Karşıt", "Akraba", "ilişkin"), 2, "içe aktarım", 1, null, "Gemini cevap veremedi")
        )
        assertEquals(l, TopluKontrol.oku(TopluKontrol.yaz(l)))
    }
}
