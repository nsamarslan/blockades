package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Joker bedelleri şık sanılmasın, sayı şıkları da elenmesin.
 *
 * Günlükte «200»@%89 «200»@%89 «100»@%89 satırları, ekranın altındaki
 * joker düğmelerinin bedelleri. Şık bölgesinin alt ucuna girdikleri ve
 * sayı süzgeci orada bilerek gevşetildiği için şık adayı oluyorlardı.
 *
 * Ayırt edici işaret metin değil DİZİLİM: rozetler aynı satırda yan yana,
 * şıklar alt alta tek tek durur. ("65 / 1 / 16 / 165" sorusunun dört şıkkı
 * da salt sayı; metne bakarak ayırmak mümkün değil.)
 */
class NumberStripTest {

    @Test
    fun `yan yana uc rozet seridi elenir`() {
        assertTrue(QuestionParser.isNumberStrip(listOf("200", "200", "100")))
        assertTrue(QuestionParser.isNumberStrip(listOf("50", "50", "200", "100")))
    }

    @Test
    fun `alt alta duran sayi siklari ayri satirlardadir`() {
        // Her şık kendi satırında; satır başına tek metin düşer.
        listOf("65", "1", "16", "165").forEach {
            assertFalse(QuestionParser.isNumberStrip(listOf(it)))
        }
    }

    @Test
    fun `iki sayilik satir serit sayilmaz`() {
        // Eşik üç: iki sayı yan yana ise bu bir rozet şeridi değildir.
        assertFalse(QuestionParser.isNumberStrip(listOf("200", "100")))
    }

    @Test
    fun `icinde harf olan satir serit degildir`() {
        assertFalse(QuestionParser.isNumberStrip(listOf("Ay", "Jüpiter", "Neptün")))
        assertFalse(QuestionParser.isNumberStrip(listOf("200", "100", "4.6 milyar")))
    }
}
