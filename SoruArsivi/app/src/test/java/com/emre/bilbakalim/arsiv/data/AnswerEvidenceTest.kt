package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.data.Repo.AnswerEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arşivdeki doğru cevabın hangi gözlemle değiştirilebileceği.
 *
 * Bu kural, otomatik modda kendini besleyen bir hatayı kesiyor: arşive bir
 * kez yanlış cevap girdiğinde bot her turda ona basıyor ve hata kalıcı
 * oluyor. Zayıf gözlem üstüne yazamaz, eşit güçte gözlem yazabilir —
 * ikincisi olmadan bozuk eski kayıtlar hiç düzelmezdi.
 */
class AnswerEvidenceTest {

    @Test
    fun `zayif gozlem guclu kaydin ustune yazmaz`() {
        // "Dokunuş" en zayıf kanıt: dokunulan şık doğru olmayabilir.
        assertTrue(Repo.shouldKeepStored(1, AnswerEvidence.CERTAIN.label, AnswerEvidence.TOUCH))
        assertTrue(Repo.shouldKeepStored(1, AnswerEvidence.GREEN.label, AnswerEvidence.TOUCH))
        assertTrue(Repo.shouldKeepStored(1, AnswerEvidence.TIMEOUT.label, AnswerEvidence.TOUCH))
    }

    @Test
    fun `esit ya da guclu gozlem ustune yazar`() {
        assertFalse(Repo.shouldKeepStored(1, AnswerEvidence.GREEN.label, AnswerEvidence.GREEN))
        assertFalse(Repo.shouldKeepStored(1, AnswerEvidence.GREEN.label, AnswerEvidence.CERTAIN))
        assertFalse(Repo.shouldKeepStored(1, AnswerEvidence.TIMEOUT.label, AnswerEvidence.CERTAIN))
    }

    @Test
    fun `cevabi olmayan kayda her gozlem yazilir`() {
        AnswerEvidence.entries.forEach {
            assertFalse("$it yazabilmeli", Repo.shouldKeepStored(null, null, it))
        }
    }

    @Test
    fun `ice aktarilmis cevap zayif sayilir`() {
        // Yedekten gelen cevabın üstüne ekrandan okunan her gözlem yazabilir.
        assertFalse(Repo.shouldKeepStored(1, "içe aktarım", AnswerEvidence.GREEN))
        assertFalse(Repo.shouldKeepStored(1, "içe aktarım", AnswerEvidence.CERTAIN))
    }

    @Test
    fun `kaynagi bilinmeyen eski kayit her gozlemle guncellenir`() {
        // Bu sürümden önce yazılmış kayıtların kaynağı tanınmıyor; onların
        // düzelebilmesi için en zayıf gözlemin bile geçmesi gerekiyor.
        assertFalse(Repo.shouldKeepStored(1, null, AnswerEvidence.TOUCH))
        assertFalse(Repo.shouldKeepStored(1, "bilinmeyen", AnswerEvidence.TOUCH))
    }
}
