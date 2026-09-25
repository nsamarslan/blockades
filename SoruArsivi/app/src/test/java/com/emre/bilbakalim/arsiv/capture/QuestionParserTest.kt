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
    fun `puan balonu sik sanilmaz`() {
        // Doğru cevaptan sonra şıkkın üstüne düşen "+5"; arşivde şıkkı
        // "5 +5" olarak kaydedilmiş sorular bundan.
        listOf("+5", "5 +5", "+10", "+ 5").forEach {
            assertFalse("\"$it\" elenmeli", keptInOptions(it))
            assertFalse("\"$it\" elenmeli", keptOutside(it))
        }
        // Şık bölgesinin dışında eksili balon da çöp.
        assertFalse(keptOutside("-5"))
    }

    @Test
    fun `sik bolgesinde eksili sayi siktir`() {
        // "(-4)² neye eşittir?" gibi sorularda şıklar "-16 / -4 / 4 / 16".
        // Eskiden eksili olanlar balon sanılıp atılıyordu.
        listOf("-16", "-4", "-5", "-0,5").forEach {
            assertTrue("\"$it\" şık olarak kalmalı", keptInOptions(it))
        }
    }

    @Test
    fun `joker dugmeleri sik sanilmaz`() {
        // Ekranın altındaki üç joker: 50/50, çift cevap, soru değiştir.
        listOf("50/50", "50 50", "x2", "Çift Cevap", "Soru Değiştir").forEach {
            assertFalse("\"$it\" elenmeli", keptInOptions(it))
        }
    }

    @Test
    fun `eksi isaretli sayi sik olabilir mi diye metne bakilir`() {
        // Sayı şıklar korunmalı; ayıran şey artı/eksi işareti.
        assertTrue(keptInOptions("25"))
        assertTrue(keptInOptions("1923"))
        assertFalse(keptInOptions("+25"))
    }

    @Test
    fun `soru numarasi balonu soru metnine yapismaz`() {
        // "1)" ve "(44)" gibi parçalar süzgeçten kaçıp metne yapışıyordu.
        assertFalse(keptOutside("1)"))
        assertFalse(keptOutside("(44)"))
        assertFalse(keptOutside("1."))
    }
}
