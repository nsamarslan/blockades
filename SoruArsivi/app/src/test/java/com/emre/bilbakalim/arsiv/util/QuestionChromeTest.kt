package com.emre.bilbakalim.arsiv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `buyuk kucuk harf farki olan siklar ayrilir`() {
        // Gerçek günlük: çap "R", yarıçap "r". İlk kademe küçük harfe
        // indirdiği için ikisi birden tutuyor, cevap hiç kullanılamıyordu.
        val siklar = listOf("R", "V", "Z+", "r")
        assertEquals(0, TurkishText.matchIndex(siklar, "R"))
        assertEquals(3, TurkishText.matchIndex(siklar, "r"))
    }

    @Test
    fun `kayittaki tek harf hatasi eslesir`() {
        // Gerçek günlük: arşivde "Öklic", ekranda "Öklid"; eşleşmediği için
        // bot bildiği cevabı bırakıp rastgele basıyordu.
        val ekran = listOf("Pisagor", "Arşimet", "Zenon", "Öklid")
        assertEquals(3, TurkishText.matchIndex(ekran, "Öklic"))
        // Sayılarda tek hane başka cevap.
        assertEquals(null, TurkishText.matchIndex(listOf("166", "179", "159", "189"), "169"))
        // İki şık birden tutuyorsa tahmin yok.
        assertEquals(null, TurkishText.matchIndex(listOf("Kosinüs", "Kosinüz", "Sinüs"), "Kosinüa"))
    }

    @Test
    fun `bastaki icerik etiketi sokuluyor`() {
        val soru = "10 kişilik bir bilet kuyruğunda kaç kişi bulunur?"
        for (etiket in listOf("A69)", "h64)", "h48¢", "fo9;", "Ks79", "ho84", "S179.", "B83)")) {
            assertEquals(etiket, soru, TurkishText.stripQuestionChrome("$etiket $soru"))
        }
        assertEquals("Dik açı kaç derecedir?", TurkishText.soruEtiketiniAt("S179. Dik açı kaç derecedir?"))
    }

    @Test
    fun `dort islem simgesi soru basindan atiliyor`() {
        // Eski sürüm kartın üstündeki "2x2=4" simgesini soruya katıyordu;
        // OCR "4"ü "H" ya da "+" okuyabiliyor.
        assertEquals("(4*8)+4 =?", TurkishText.soruEtiketiniAt("2x2=H (4*8)+4 =?"))
        assertEquals("(4x4)-(10+11) =?", TurkishText.stripQuestionChrome("2x2=H (4x4)-(10+11) =?"))
        assertEquals("(4×8)+4 = ?", TurkishText.soruEtiketiniAt("2x2=4 (4×8)+4 = ?"))
        assertEquals("(4×8)+4 = ?", TurkishText.soruEtiketiniAt("2x2= 4 (4×8)+4 = ?"))
        assertEquals("12+7 = ?", TurkishText.soruEtiketiniAt("2x2=+ 12+7 = ?"))
        // Sorunun kendisi "2x2" ise dokunulmuyor.
        assertEquals("2x2 = ? işleminin sonucu kaçtır?", TurkishText.soruEtiketiniAt("2x2 = ? işleminin sonucu kaçtır?"))
        assertEquals("2x2=?", TurkishText.soruEtiketiniAt("2x2=?"))
    }

    @Test
    fun `kisa soru en az iki harf ya da rakam`() {
        assertTrue(TurkishText.kisaSoruYeterli("Remedy"))
        assertTrue(TurkishText.kisaSoruYeterli("Go"))
        assertTrue(TurkishText.kisaSoruYeterli("5+3=?"))
        assertFalse(TurkishText.kisaSoruYeterli("?"))
        assertFalse(TurkishText.kisaSoruYeterli("A -"))
    }

    @Test
    fun `etikete benzeyen gercek soru basi sokulmuyor`() {
        for (soru in listOf(
            "15 kişilik bir kuyrukta sağdan sayıldığında 8. sırada bulunan Ahmet soldan kaçıncı sırada yer alır?",
            "12:3 = 4 işleminde \"bölüm\" hangisidir?",
            "8'li sayı sisteminde kaç rakam vardır?",
            "B12 vitamini eksikliğinde hangi hastalık görülür?",
            "CO2 gazının kimyasal adı nedir?",
            "O! işleminin sonucu kaçtır?",
            "Aylin 8, ablası 10, annesi 32 yaşındadır."
        )) {
            assertEquals(soru, TurkishText.soruEtiketiniAt(soru))
        }
    }
}
