package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Eski şık işareti kuralının bozduğu kayıtların onarımı.
 *
 * O kural "1. Dönem"deki "1."i şık işareti sanıp siliyordu. Arşivde dört
 * şıkkı da "Dönem" olan kayıtlar kendiliğinden düzelmiyordu: soru yeniden
 * okunduğunda bulanık eşleşme eski kaydı buluyor, ama şıklar yalnızca liste
 * kısaysa tamamlandığı için bozuk hâl kalıyordu.
 */
class SiraSayisiOnarimTest {

    private val donemler = listOf("1. Dönem", "4. Dönem", "2. Dönem", "3. Dönem")

    @Test
    fun `dort ayni sik sira sayilariyla onarilir`() {
        // Kayıttaki "Dönem" cevabı dört şıktan hangisi olduğu bilinmediği
        // için boşaltılıyor; bir sonraki renk okuması yeniden öğretir.
        val onarim = Repo.siraSayisiOnarimi(List(4) { "Dönem" }, "Dönem", donemler)
        assertEquals(Repo.Onarim(null), onarim)
    }

    @Test
    fun `ayirt edilebilen cevap yeni sirasiyla korunur`() {
        val eski = listOf("Murat", "Mahmut", "Selim", "Ahmet")
        val ekran = listOf("2. Mahmut", "3. Selim", "1. Ahmet", "4. Murat")
        assertEquals(Repo.Onarim(1), Repo.siraSayisiOnarimi(eski, "Selim", ekran))
    }

    @Test
    fun `cevabi olmayan kayit da onarilir`() {
        assertEquals(Repo.Onarim(null), Repo.siraSayisiOnarimi(List(4) { "Dönem" }, null, donemler))
    }

    @Test
    fun `saglam kayda dokunulmaz`() {
        assertNull(Repo.siraSayisiOnarimi(donemler, "2. Dönem", donemler.reversed()))
    }

    @Test
    fun `baska sebeple farkli siklar onarim sayilmaz`() {
        // Kayıt bozulmanın tam karşılığı değilse (başka bir kelime, OCR
        // hatası) hiçbir şey yazılmıyor.
        val eski = listOf("Dönem", "Dönem", "Dönem", "Çağ")
        assertNull(Repo.siraSayisiOnarimi(eski, null, donemler))
        assertNull(Repo.siraSayisiOnarimi(listOf("Platon", "Kant"), null, listOf("Hume", "Locke")))
    }

    @Test
    fun `ekranda da ayirt edilemeyen siklarla onarim yapilmaz`() {
        assertNull(
            Repo.siraSayisiOnarimi(
                List(4) { "Dönem" }, null, listOf("1. Dönem", "1. Dönem", "2. Dönem", "3. Dönem")
            )
        )
    }
}
