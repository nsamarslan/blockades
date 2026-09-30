package com.emre.bilbakalim.arsiv.util

import com.emre.bilbakalim.arsiv.data.QuestionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DisaAktarimSirasiTest {

    private fun soru(id: Long, kez: Int, zaman: Long) =
        QuestionEntity(id = id, questionText = "Soru $id?", fingerprint = "p$id", seenCount = kez, capturedAt = zaman)

    private val sorular = listOf(soru(1, 3, 100), soru(2, 18, 300), soru(3, 1, 50), soru(4, 18, 200), soru(5, 3, 100))

    @Test
    fun `en sik cikan once, esitlerde once kaydedilen`() {
        // 18 kez: #4 (200) #2'den (300) önce kaydedildi. 3 kez: aynı anda, küçük numara önde.
        assertEquals(listOf(4L, 2L, 1L, 5L, 3L), DisaAktarimSirasi.EN_SIK.sirala(sorular).map { it.id })
    }

    @Test
    fun `kayit sirasi`() {
        assertEquals(listOf(3L, 1L, 5L, 4L, 2L), DisaAktarimSirasi.KAYIT.sirala(sorular).map { it.id })
    }
}
