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
    fun `sayinin eksisi sus sayilip kirpilmaz`() {
        // Gerçek günlük: "-16 / -4 / 4 / 16" şıkları "16 / 4 / 4 / 16" okunuyordu.
        assertEquals("-16", TurkishText.cleanOcr("-16"))
        assertEquals("-4", TurkishText.cleanOcr(" -4 "))
        assertEquals("-4", TurkishText.cleanOcr("- 4"))
        assertEquals("-3/4", TurkishText.cleanOcr("−3/4"))
        assertEquals("-12", TurkishText.cleanOcr("–12"))
        // Harften önceki çizgi hâlâ süs.
        assertEquals("Ankara", TurkishText.cleanOcr("- Ankara"))
        assertEquals("Ankara", TurkishText.cleanOcr("— Ankara —"))
        assertEquals("16", TurkishText.cleanOcr("16-"))
    }

    @Test
    fun `eksili siklar eslestirmede ayrilir`() {
        val siklar = listOf("-16", "-4", "4", "16")
        assertEquals(1, TurkishText.matchIndex(siklar, "-4"))
        assertEquals(2, TurkishText.matchIndex(siklar, "4"))
        assertEquals(3, TurkishText.matchIndex(siklar, "16"))
        assertEquals(0, TurkishText.matchIndex(siklar, TurkishText.cleanOcr("−16")))
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
