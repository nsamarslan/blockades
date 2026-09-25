package com.emre.bilbakalim.arsiv.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Soru metnine yapışan arayüz parçalarının sökülmesi.
 *
 * Değerlerin hepsi arşivde gerçekten böyle kaydedilmiş sorulardan. Bunlar
 * parça bazlı süzgeçlerden kaçıyor çünkü OCR onları soruyla **aynı blokta**
 * döndürüyor; metnin kendisinden sökmek tek yol.
 */
class QuestionChromeTest {

    @Test
    fun `sondaki puan balonu sokulur`() {
        assertEquals(
            "Teleskopla Dünya'dan bakıldığında yüzey şekilleri gözlenebilen gezegen hangisidir?",
            TurkishText.stripQuestionChrome(
                "Teleskopla Dünya'dan bakıldığında yüzey şekilleri gözlenebilen gezegen hangisidir? +5"
            )
        )
        assertEquals("Kaç kıta vardır?", TurkishText.stripQuestionChrome("Kaç kıta vardır? +10"))
        assertEquals("Kaç kıta vardır?", TurkishText.stripQuestionChrome("Kaç kıta vardır? -5"))
    }

    @Test
    fun `bastaki KOMBO banneri sokulur`() {
        assertEquals(
            "Türkiye'nin uzay bilimleri programı hangisidir?",
            TurkishText.stripQuestionChrome("KOMBO Türkiye'nin uzay bilimleri programı hangisidir?")
        )
        assertEquals(
            "Hangisi doğrudur?",
            TurkishText.stripQuestionChrome("Muhteşem! Hangisi doğrudur?")
        )
        assertEquals(
            "Hangisi doğrudur?",
            TurkishText.stripQuestionChrome("Çok Yaklaştın! Hangisi doğrudur?")
        )
    }

    @Test
    fun `soru numarasi ve sure uyarisi hala sokuluyor`() {
        assertEquals("Hangisi doğrudur?", TurkishText.stripQuestionChrome("17. Hangisi doğrudur?"))
        assertEquals("Hangisi doğrudur?", TurkishText.stripQuestionChrome("Süre Bitti! Hangisi doğrudur?"))
        // Üst üste binmiş fazlalıklar
        assertEquals(
            "Hangisi doğrudur?",
            TurkishText.stripQuestionChrome("17. Süre Bitti Hangisi doğrudur? +5")
        )
    }

    @Test
    fun `sorunun kendi sayilari bozulmaz`() {
        // Metnin içindeki ve sonundaki gerçek sayılar korunmalı.
        val soru = "1919'da başlayan Kurtuluş Savaşı kaç yıl sürdü?"
        assertEquals(soru, TurkishText.stripQuestionChrome(soru))
        assertEquals("Sonuç kaçtır: 2 + 2", TurkishText.stripQuestionChrome("Sonuç kaçtır: 2 + 2"))
        assertEquals("Hangi yılda kuruldu 1923", TurkishText.stripQuestionChrome("Hangi yılda kuruldu 1923"))
    }

    @Test
    fun `temiz metne dokunulmaz`() {
        val soru = "Kilise hangi çağda kültürün merkezi haline gelmiştir?"
        assertEquals(soru, TurkishText.stripQuestionChrome(soru))
    }
}
