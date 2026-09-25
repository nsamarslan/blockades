package com.emre.bilbakalim.arsiv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Şık eşleştirmesinin testleri.
 *
 * Bu mantık iki yerde birden taşıyor: otomatik mod bilinen cevaba dokunurken
 * ve yakalanan cevap kayda yazılırken. Oyun şıkları her turda karıştırdığı
 * için ikisinde de sıra değil metin esas alınmak zorunda — yanlış eşleşme
 * doğrudan arşive yanlış cevap yazar.
 */
class TurkishTextTest {

    private val stored = listOf("Platon", "Aristoteles", "Sokrates", "Descartes")

    @Test
    fun `karisik sirada dogru siki bulur`() {
        // Kayıtta doğru cevap "Sokrates" (2. sıra); ekranda 0. sıraya düşmüş.
        val screen = listOf("Sokrates", "Descartes", "Platon", "Aristoteles")
        assertEquals(0, TurkishText.matchIndex(screen, stored[2]))
        assertEquals(2, TurkishText.matchIndex(screen, stored[0]))
    }

    @Test
    fun `buyuk kucuk harf ve Turkce aksan farkini yok sayar`() {
        val screen = listOf("İstanbul", "Ankara", "İzmir", "Muğla")
        assertEquals(0, TurkishText.matchIndex(screen, "ISTANBUL"))
        assertEquals(3, TurkishText.matchIndex(screen, "mugla"))
    }

    @Test
    fun `OCR bir iki harfi yanlis okuduysa yine bulur`() {
        val screen = listOf("Mimar Sinam", "Kanuni Sultan Süleyman", "Fatih", "Yavuz")
        assertEquals(0, TurkishText.matchIndex(screen, "Mimar Sinan"))
    }

    @Test
    fun `sik isareti ve noktalama farki eslesmeyi bozmaz`() {
        val screen = listOf("A) Platon", "B) Aristoteles", "C) Sokrates", "D) Descartes")
        assertEquals(2, TurkishText.matchIndex(screen, "Sokrates"))
    }

    @Test
    fun `benzemeyen metinde null doner`() {
        // Yanlış şıkka basmaktansa bilmediğimizi söylemek yeğdir:
        // çağıran taraf bu durumda rastgele seçiyor.
        assertNull(TurkishText.matchIndex(stored, "Immanuel Kant"))
        assertNull(TurkishText.matchIndex(stored, null))
        assertNull(TurkishText.matchIndex(emptyList(), "Platon"))
    }

    @Test
    fun `sik isareti soyulunca bos kalan metinler ayirt edilir`() {
        // Ayrıştırıcı, boş kalan şıkları metin **ve** kutu listesinden birlikte
        // atmak zorunda. Eskiden yalnızca metin atılıyordu ve listeler kayıyordu:
        // "2. şıkkın metni" ile "2. şıkkın kutusu" başka şıklara ait oluyordu.
        assertEquals("", TurkishText.stripOptionPrefix("A) ").trim())
        assertEquals("Platon", TurkishText.stripOptionPrefix("A) Platon"))
    }

    @Test
    fun `ilk kayitta sira degismez`() {
        // Soru ilk kez kaydedilirken iki liste aynıdır; eşleştirme sırayı
        // olduğu gibi bırakmalı.
        stored.indices.forEach { i ->
            assertEquals(i, TurkishText.matchIndex(stored, stored[i]))
        }
    }
    @Test
    fun `sira sayisi sik isareti sayilmaz`() {
        // Gerçek arıza: "1. Dönem / 4. Dönem / 2. Dönem / 3. Dönem" şıkları
        // arşive dört kez "Dönem" diye yazılmıştı. Türkçede sıra sayısı
        // rakam ve noktayla yazılır; bu bir şık işareti değil.
        assertEquals("1. Dönem", TurkishText.stripOptionPrefix("1. Dönem"))
        assertEquals("4. Dönem", TurkishText.stripOptionPrefix("4. Dönem"))
        assertEquals("2. Mahmut", TurkishText.stripOptionPrefix("2. Mahmut"))
    }

    @Test
    fun `gercek sik isaretleri yine soyulur`() {
        assertEquals("Platon", TurkishText.stripOptionPrefix("A) Platon"))
        assertEquals("Platon", TurkishText.stripOptionPrefix("b. Platon"))
        assertEquals("Platon", TurkishText.stripOptionPrefix("1) Platon"))
        assertEquals("Platon", TurkishText.stripOptionPrefix("(2) Platon"))
    }

    @Test
    fun `olumsuzluk eki farki`() {
        fun fark(a: String, b: String) = TurkishText.olumsuzlukEkiFarki(TurkishText.words(a), TurkishText.words(b))
        assertTrue(fark("3 gelme olasılığı kaçtır?", "3 gelmeme olasılığı kaçtır?"))
        assertTrue(fark("Hangisi yapılır?", "Hangisi yapılmaz?"))
        assertTrue(fark("Su kaç derecede donar?", "Su kaç derecede donmaz?"))
        assertTrue(fark("Hangisi okunur?", "Hangisi okunmaz?"))
        // OCR harf hataları ve ilgisiz farklar olumsuzluk sayılmaz.
        assertFalse(fark("Jüpiter'in uydularin sayısı", "Jüpiter'in uydulariin sayısı"))
        assertFalse(fark("Hangisi gezegendir?", "Hangisi gezegen?"))
        assertFalse(fark("Mars kaçıncı gezegendir?", "Dünya kaçıncı gezegendir?"))
        assertFalse(fark("Aynı soru", "Aynı soru"))
    }
}
