package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.capture.QuestionParser.Parca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Soru kartının üstündeki başlık satırı soru metnine karışmasın.
 *
 * Konumlar gerçek ekran görüntülerinden (900x2000): İngilizce Lügat'ta
 * "Remedy" sorusu, 4 İşlem'de "(4×8)+4 = ?" sorusu. Başlıkta solda soru
 * numarası, ortada kategori simgesi ("A-Z" / "2x2=4"), sağda sayaç var.
 * Günlükte soru "A-Z Remedy" (kaydedilmedi, bot hiç dokunmadı) ve
 * "2x2=H (4*8)+4 =?" (etiketiyle kaydedildi) okunuyordu.
 */
class BaslikSatiriTest {

    private val h = 2000
    private val ilkSikUst = 1052
    private val numaraX = 0..360
    private val sayacX = 540..900

    private val yildiz = Parca("10", 215, 165, 255, 205)
    private val altin = Parca("41.445", 600, 165, 705, 205)
    private val seviye = Parca("51", 430, 245, 470, 285)
    private val serit = listOf(175, 267, 358, 450, 541, 632, 724)
        .mapIndexed { i, x -> Parca("${i + 1}", x - 20, 310, x + 20, 370) }
    private val numara = Parca("2.", 130, 485, 160, 520)
    private val sayac = Parca("74", 735, 480, 780, 520)
    private val simge = Parca("A-Z", 435, 475, 475, 505)
    private val soru = Parca("Remedy", 360, 760, 540, 805)

    private fun baslik(hepsi: List<Parca>, havuz: List<Parca>) =
        QuestionParser.baslikSatiri(hepsi, havuz, h, ilkSikUst, numaraX, sayacX)

    @Test
    fun `numara ve sayacin satiri baslik sayilir`() {
        val hepsi = listOf(yildiz, altin, seviye) + serit + listOf(numara, simge, sayac, soru)
        val havuz = listOf(simge, soru)
        val sinir = baslik(hepsi, havuz)
        assertEquals(520, sinir)
        assertEquals(listOf(soru), havuz.filter { it.ortaY > sinir!! })
    }

    @Test
    fun `numara okunamazsa sayac yetiyor`() {
        assertEquals(520, baslik(listOf(yildiz, simge, Parca("(78)", 730, 480, 785, 520), soru), listOf(simge, soru)))
        assertEquals(520, baslik(listOf(simge, Parca("87)", 735, 480, 780, 520), soru), listOf(simge, soru)))
    }

    @Test
    fun `sayac okunamazsa numara yetiyor`() {
        assertEquals(520, baslik(listOf(yildiz, seviye, numara, simge, soru), listOf(simge, soru)))
    }

    @Test
    fun `ilerleme seridi baslik sayilmaz`() {
        // Numara da sayaç da okunamadı: şeritteki "1" numaraya benziyor ama
        // aynı satırda yedi sayı var.
        assertNull(baslik(serit + listOf(simge, soru), listOf(simge, soru)))
    }

    @Test
    fun `kartin icindeki sayi baslik sayilmaz`() {
        // Resimli soru: kartın içinde, soru metninin altında tek başına "12".
        val metin = Parca("Saatin akrebi hangi sayıyı gösteriyor?", 150, 600, 750, 640)
        val resimdeki = Parca("12", 200, 760, 240, 800)
        val hepsi = listOf(numara, simge, sayac, metin, resimdeki)
        assertEquals(520, baslik(hepsi, listOf(simge, metin)))
    }

    @Test
    fun `sayi yoksa baslik yok`() {
        assertNull(baslik(listOf(simge, soru), listOf(simge, soru)))
    }

    @Test
    fun `uzak iki kisa parca birlesmez`() {
        // Başlık bulunamasa da simge ile soru birleşmiyor: aralarında boşluk var.
        assertEquals("Remedy", QuestionParser.assembleQuestion(listOf(simge, soru), minUzunluk = 1)?.metin)
        val islem = Parca("(4×8)+4 = ?", 330, 760, 565, 805)
        val etiket = Parca("2x2=4", 385, 475, 515, 510)
        val kurulan = QuestionParser.assembleQuestion(listOf(etiket, islem))
        assertEquals("(4×8)+4 = ?", kurulan?.metin)
        assertEquals(805, kurulan?.alt)
    }

    @Test
    fun `bitisik kisa satirlar birlesir`() {
        val ust = Parca("En büyük", 300, 740, 600, 780)
        val alt = Parca("gezegen?", 320, 790, 580, 830)
        val kurulan = QuestionParser.assembleQuestion(listOf(alt, ust))
        assertEquals("En büyük gezegen?", kurulan?.metin)
        assertEquals(830, kurulan?.alt)
    }

    @Test
    fun `kisa soru ancak izinle kurulur`() {
        assertNull(QuestionParser.assembleQuestion(listOf(soru)))
        assertEquals("Remedy", QuestionParser.assembleQuestion(listOf(soru), minUzunluk = 1)?.metin)
    }
}
