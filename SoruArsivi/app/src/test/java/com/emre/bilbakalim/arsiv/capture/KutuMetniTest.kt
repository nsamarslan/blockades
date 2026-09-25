package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Şık kutusunun içinden okunan OCR parçalarının birleştirilmesi.
 *
 * Gerçek günlük: "6" şıkkı üç ayrı soruda "6 6" okundu, önceki günlükte de
 * "4 4". ML Kit rakamı iki kez döndürüyor; kaydedilen "6 6" ekrandaki "6"
 * ile bir daha hiç eşleşmiyordu.
 */
class KutuMetniTest {

    private fun metin(vararg p: Pair<String, IntRange>) = QuestionParser.kutuMetni(p.toList())

    @Test
    fun `iki kez okunan rakam bir kez yazilir`() {
        assertEquals("6", metin("6" to 500..540, "6" to 500..540))
        assertEquals("6", metin("6" to 500..540, "6" to 560..600))
        assertEquals("1/6", metin("1/6" to 480..560, "1/6" to 482..558))
    }

    @Test
    fun `yan yana gercek tekrar korunur`() {
        assertEquals("Beri Beri", metin("Beri" to 300..380, "Beri" to 400..480))
    }

    @Test
    fun `ust uste binen ayni kelime bir kez`() {
        assertEquals("Kara delik", metin("Kara" to 300..380, "Kara" to 305..378, "delik" to 400..480))
    }

    @Test
    fun `farkli parcalar birlesir`() {
        assertEquals("25 cm", metin("25" to 300..340, "cm" to 350..390))
        assertEquals("Kara delik", metin("Kara\ndelik" to 300..480))
        assertEquals("", metin(" " to 0..10))
    }
}
