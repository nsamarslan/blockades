package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Süre doldu" kuralının testleri.
 *
 * Bu kural yanlış tetiklendiğinde arşive **yanlış bir doğru cevap** yazılıyor
 * ve otomatik mod sonraki turlarda o yanlış şıkka basmaya başlıyor. Yani
 * hatası sessiz ve kalıcı. Değerlerin hepsi gerçek ekran günlüklerinden.
 */
class AnswerColorDetectorTest {

    private fun renk(vararg c: IntArray) = AnswerColorDetector.dimmedOutlier(c.toList())

    @Test
    fun `cevaplanmamis beyaz ekranda karar verilmez`() {
        assertNull(
            renk(
                intArrayOf(248, 248, 248), intArrayOf(248, 248, 248),
                intArrayOf(248, 248, 248), intArrayOf(248, 248, 248)
            )
        )
    }

    @Test
    fun `beliris animasyonu sure dolmus sanilmaz`() {
        // Üç şık aynı anda vurgulanmış, biri henüz beyaz. Ekran karartılmış
        // değil — parlaklık 0.97. Kural buna karar vermemeli.
        assertNull(
            renk(
                intArrayOf(248, 216, 88), intArrayOf(248, 216, 88),
                intArrayOf(248, 216, 88), intArrayOf(200, 216, 120)
            )
        )
    }

    @Test
    fun `tek sik vurgulanmisken karar verilmez`() {
        // Gerçek günlükten: bir şık sarıya dönmüş, üçü beyaz.
        assertNull(
            renk(
                intArrayOf(248, 216, 88), intArrayOf(248, 248, 248),
                intArrayOf(248, 248, 248), intArrayOf(248, 248, 248)
            )
        )
    }

    @Test
    fun `ekran gecisinde sonen siklara karar verilmez`() {
        // Gerçek günlükten: soru değişirken bir kare boyunca üç şık koyu,
        // biri beyaz kalıyor. Parlak olan doğru cevap değil, geçiş karesi.
        assertNull(
            renk(
                intArrayOf(248, 248, 248), intArrayOf(88, 88, 152),
                intArrayOf(88, 88, 152), intArrayOf(88, 88, 152)
            )
        )
        assertNull(
            renk(
                intArrayOf(200, 184, 232), intArrayOf(184, 184, 216),
                intArrayOf(184, 184, 216), intArrayOf(184, 184, 216)
            )
        )
    }

    @Test
    fun `gercekten karartilmis ekranda dogru cevap bulunur`() {
        // Kod yorumundaki ölçüm: dokunulmamış şıklar RGB(80,80,140),
        // doğru cevap RGB(80,40,100) — daha koyu ve daha doygun.
        assertEquals(
            2,
            renk(
                intArrayOf(80, 80, 140), intArrayOf(80, 80, 140),
                intArrayOf(80, 40, 100), intArrayOf(80, 80, 140)
            )
        )
    }

    @Test
    fun `kucuk renk farklari karar saymaz`() {
        // Gerçek günlükten: koyu ekranda şıklar arasında birkaç birimlik
        // fark var ama hiçbiri "ayrışıyor" denecek kadar uzak değil.
        assertNull(
            renk(
                intArrayOf(56, 24, 136), intArrayOf(56, 24, 136),
                intArrayOf(56, 24, 136), intArrayOf(40, 24, 120)
            )
        )
    }
}
