package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Metin süzgecinin testleri.
 *
 * Değerler gerçek bir ekrandan alındı: şıkları "25 / 55 / 5 / 15" olan bir
 * matematik sorusu. Sayı süzgeci bütün ekrana uygulandığında dört şık birden
 * eleniyor, soru hiç yakalanamıyor ve otomatik mod dokunacak şık bulamıyordu.
 *
 * Aynı ekranda "55" hem sayaç (tepede) hem şık (altta) olarak geçiyor —
 * yani bu ayrımı metne bakarak yapmak mümkün değil, konum şart. Testler
 * ikisini de kapsıyor.
 */
class QuestionParserTest {

    private fun keptInOptions(text: String) = !QuestionParser.isChrome(text, inOptionArea = true)
    private fun keptOutside(text: String) = !QuestionParser.isChrome(text, inOptionArea = false)

    @Test
    fun `sayi olan siklar sik bolgesinde kalir`() {
        listOf("25", "55", "5", "15").forEach {
            assertTrue("\"$it\" şık olarak kalmalı", keptInOptions(it))
        }
    }

    @Test
    fun `ayni sayilar sik bolgesinin disinda elenir`() {
        // Tepedeki sayaç, altın sayısı, soru numarası balonları.
        listOf("55", "480", "0", "1", "7", "200").forEach {
            assertFalse("\"$it\" arayüz sayısı olarak elenmeli", keptOutside(it))
        }
    }

    @Test
    fun `sure bildirimi hicbir yerde sik degildir`() {
        assertFalse(keptInOptions("+10 sn"))
        assertFalse(keptInOptions("-4 saniye"))
        assertFalse(keptOutside("+10 sn"))
    }

    @Test
    fun `noktalama parcalari her yerde elenir`() {
        listOf("•", "...", "—", "|", " ").forEach {
            assertFalse("\"$it\" elenmeli", keptInOptions(it))
            assertFalse("\"$it\" elenmeli", keptOutside(it))
        }
    }

    @Test
    fun `soru metni ve sozcuk siklar korunur`() {
        val soru = "Bir koşuda 1. atletin 2. atletle mesafesi 20 m, 3. atletle " +
            "mesafesi 35 metre ise 2. ve 3. atletler arası kaç metredir?"
        assertTrue(keptOutside(soru))
        assertTrue(keptInOptions("Platon"))
        assertTrue(keptOutside("Platon"))
        // Kısa sözcük şıklar da düşmemeli.
        assertTrue(keptInOptions("Üç"))
        assertTrue(keptOutside("Altı"))
    }

    @Test
    fun `arayuz sozcukleri her iki bolgede de elenir`() {
        listOf("Puan", "Süre", "Çıkış", "Tekrar", "İpucu").forEach {
            assertFalse("\"$it\" elenmeli", keptInOptions(it))
            assertFalse("\"$it\" elenmeli", keptOutside(it))
        }
    }

    @Test
    fun `soru numarasi balonu soru metnine yapismaz`() {
        // "1)" ve "(44)" gibi parçalar süzgeçten kaçıp metne yapışıyordu.
        assertFalse(keptOutside("1)"))
        assertFalse(keptOutside("(44)"))
        assertFalse(keptOutside("1."))
    }
}
