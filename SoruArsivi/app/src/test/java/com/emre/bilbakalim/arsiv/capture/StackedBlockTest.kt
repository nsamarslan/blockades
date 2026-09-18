package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ML Kit'in tek bloğa topladığı şıkları geri ayırmak.
 *
 * Şıkları kısa sayılar olan sorularda ("240 / 1 / 365 / 24") dört rakam
 * alt alta, aynı yazı boyunda ve aynı eksende durduğu için ML Kit dördünü
 * TEK bloğa topluyor. Blok metni "240\n1\n365\n24" oluyor; cleanOcr satır
 * sonlarını boşluğa çevirince de elimizde dört şıkkın yerine
 * "240 1 365 24" yazan, kutusu dört şıkkı birden kapsayan tek parça
 * kalıyordu. Ayrıştırıcı şık bulamıyor, bot hiçbir tuşa basmıyordu.
 *
 * Bütün değerler gerçek bir ekrandan ölçüldü (1080x2400).
 */
class StackedBlockTest {

    /** Dört şık kutusundaki metin satırları: yükseklik 36, boşluk 171. */
    private val sikTops = listOf(1316, 1522, 1729, 1935)
    private val sikBottoms = sikTops.map { it + 36 }

    /** Soru kartındaki sarmalanmış paragraf: yükseklik 38, boşluk 34. */
    private val soruTops = listOf(882, 953)
    private val soruBottoms = listOf(920, 991)

    @Test
    fun `ayri kutulardaki siklar bolunur`() {
        val kumeler = QuestionParser.clusterRows(sikTops, sikBottoms)
        assertEquals(listOf(0..0, 1..1, 2..2, 3..3), kumeler)
    }

    @Test
    fun `sarmalanmis paragraf bolunmez`() {
        val kumeler = QuestionParser.clusterRows(soruTops, soruBottoms)
        assertEquals(listOf(0..1), kumeler)
    }

    @Test
    fun `kutusunda sarmalanan uzun sik birlikte kalir`() {
        // Üç kutu; ortadaki şık iki satıra sarmalanmış.
        val tops = listOf(1316, 1522, 1560, 1729)
        val bottoms = listOf(1352, 1558, 1596, 1765)
        assertEquals(listOf(0..0, 1..2, 3..3), QuestionParser.clusterRows(tops, bottoms))
    }

    @Test
    fun `tek satirlik blok oldugu gibi kalir`() {
        assertEquals(listOf(0..0), QuestionParser.clusterRows(listOf(100), listOf(140)))
    }

    @Test
    fun `bozuk olcu blogu bolmez`() {
        // Yükseklik sıfır ya da negatifse bölmeye kalkışmıyoruz.
        assertEquals(listOf(0..1), QuestionParser.clusterRows(listOf(10, 50), listOf(10, 90)))
        assertEquals(emptyList<IntRange>(), QuestionParser.clusterRows(emptyList(), emptyList()))
        assertEquals(emptyList<IntRange>(), QuestionParser.clusterRows(listOf(1, 2), listOf(5)))
    }
}
