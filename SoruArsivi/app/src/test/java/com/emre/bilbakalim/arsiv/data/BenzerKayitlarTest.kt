package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.data.BenzerKayitlar.Satir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenzerKayitlarTest {

    private fun s(id: Long, soru: String, vararg siklar: String) = Satir(id, soru, siklar.toList())

    @Test
    fun `ocr farki olan ayni soru aday olur`() {
        val l = listOf(
            s(1, "ldea", "Öğretmen", "Fikir", "Koltuk", "Saray"),
            s(2, "Idea", "Fikir", "Saray", "Koltuk", "Öğretmen"),
            s(3, "Without", "den itibaren", "Önünde", "SIZ /-siz", "Sonuçta"),
            s(4, "Without", "den itibaren", "Sonuçta", "Önünde", "SIZ-siz")
        )
        assertEquals(listOf(1L to 2L, 3L to 4L), BenzerKayitlar.adaylar(l))
    }

    @Test
    fun `ayni kelime farkli siklarla aday olmaz`() {
        // Bu oyunda aynı kelime farklı şık setleriyle geliyor; ayrı kalmalı.
        val l = listOf(
            s(1, "Concrete", "Beton", "Geçici", "Soyut", "Derin"),
            s(2, "Concrete", "Vakıf", "Somut", "Derin", "Gençlik")
        )
        assertTrue(BenzerKayitlar.adaylar(l).isEmpty())
    }

    @Test
    fun `siklari ayni ama sorusu cok farkli olan aday olmaz`() {
        val l = listOf(
            s(1, "Türkiye'nin en uzun nehri hangisidir?", "Fırat", "Sakarya", "Kızılırmak", "Dicle"),
            s(2, "Hangi nehir Basra Körfezi'ne dökülür?", "Fırat", "Sakarya", "Kızılırmak", "Dicle")
        )
        assertTrue(BenzerKayitlar.adaylar(l).isEmpty())
    }

    @Test
    fun `yapay zeka cevabi okunur`() {
        assertEquals(
            listOf(true, false, null),
            BenzerKayitlar.ciftCevabi("{\"1\":\"SAME\",\"2\":\"different\"}", 3)
        )
        assertEquals(listOf<Boolean?>(null, null), BenzerKayitlar.ciftCevabi("bozuk", 2))
    }

    private fun q(id: Long, opts: List<String>, correct: Int?, src: String? = null, seen: Int = 1) = QuestionEntity(
        id = id, questionText = "Idea", optionA = opts[0], optionB = opts[1], optionC = opts[2], optionD = opts[3],
        correctIndex = correct, answerSource = src, fingerprint = "f$id", seenCount = seen, capturedAt = 1000L * id
    )

    @Test
    fun `birlesince tutulan kalir ve eksigi otekinden tamamlanir`() {
        val tut = q(1, listOf("Öğretmen", "Fikir", "Koltuk", "Saray"), null, seen = 2)
        val sil = q(2, listOf("Fikir", "Saray", "Koltuk", "Öğretmen"), 0, "renk", seen = 3)
        val b = BenzerKayitlar.birlesmis(tut, sil)
        assertEquals(1, b.correctIndex) // "Fikir" tutulanın sırasında 1.
        assertEquals("renk", b.answerSource)
        assertEquals(5, b.seenCount)
        assertEquals(1000L, b.capturedAt)
        assertEquals("f1", b.fingerprint)

        // Tutulanın cevabı varsa o kalır.
        val iki = BenzerKayitlar.birlesmis(q(1, tut.options, 2, "elle"), sil)
        assertEquals(2, iki.correctIndex)
        assertEquals("elle", iki.answerSource)
        assertNull(BenzerKayitlar.birlesmis(tut, q(2, sil.options, null)).correctIndex)
    }

    @Test
    fun `geri alma icin kayit json gidip gelir`() {
        val k = q(7, listOf("a", "b", "c", "d"), 1, "renk").copy(note = "not", optionSigs = "x;y")
        assertEquals(k, BenzerKayitlar.kayitOku(BenzerKayitlar.kayitJson(k)))
    }
}
