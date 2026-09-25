package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Eksik şıklar tamamlanırken kayıtlı doğru cevabın yerini koruması.
 *
 * Arşivde 2-3 şıkla duran kayıtlar var (joker rozetleri temizlenirken
 * gerçek şıklardan bazıları da gitmişti). Soru yeniden çıktığında dört
 * şıkla tamamlanıyor — ama yeni liste EKRANIN sırasında geliyor, kayıttaki
 * sırada değil. Bu yüzden doğru cevap metniyle yeniden aranmalı.
 *
 * Buradaki tehlike sessiz: eski sıraya geri düşmek, cevabı bambaşka bir
 * şıkka kaydırıyor ve otomatik mod ondan sonra hep ona basıyor.
 */
class OptionMergeTest {

    /** Repo.save'in şık tamamlama adımının saf karşılığı. */
    private fun tamamla(eski: List<String>, eskiDogru: Int?, ekran: List<String>): Int? {
        val birlesik = (0 until 4).map { ekran.getOrNull(it) ?: eski.getOrNull(it) }
            .filterNotNull()
        val metin = eskiDogru?.let { eski.getOrNull(it) }
        return TurkishText.matchIndex(birlesik, metin)
    }

    @Test
    fun `eksik siklar tamamlaninca dogru cevap metniyle tasinir`() {
        val yeni = tamamla(
            eski = listOf("Ay", "Jüpiter"), eskiDogru = 0,
            ekran = listOf("Neptün", "Jüpiter", "Ay", "Plüton")
        )
        assertEquals(2, yeni)   // "Ay" artık 3. sırada
    }

    @Test
    fun `metin yeni listede yoksa cevap bosaltilir`() {
        // Eski kayıt "Skylab" diyor ama ekranda öyle bir şık yok. Eskiden
        // eski sıraya düşülüyordu ve cevap rastgele bir şıkka kayıyordu.
        val yeni = tamamla(
            eski = listOf("Skylab", "Mir"), eskiDogru = 0,
            ekran = listOf("ISS", "Mir", "Salyut 1", "Tiangong")
        )
        assertNull(yeni)
    }

    @Test
    fun `cevabi olmayan kayit cevapsiz kalir`() {
        assertNull(tamamla(listOf("Ay", "Jüpiter"), null, listOf("Ay", "Mars", "Jüpiter", "Venüs")))
    }
}
