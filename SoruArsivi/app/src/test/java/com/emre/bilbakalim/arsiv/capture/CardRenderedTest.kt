package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Kart ekrana oturdu mu" ölçümünün testleri.
 *
 * Bu kontrol iki şeyi birden koruyor: dokunuş kutular yerine oturmadan
 * gitmesin (yoksa bot bir şıkka basıp oyun başkasında tepki veriyor) ve
 * beliriş animasyonunun ortasından geçen bir kare karar sanılmasın.
 *
 * Eski hâli kendi kendini iptal ediyordu: "renkli bir şık varsa kart
 * hazırdır" diyordu, oysa korunmak istediğimiz şey tam da geçiş karesinin
 * yanlışlıkla renkli okunmasıydı. Bu yüzden ölçüm artık doğrudan
 * parlaklığa bakıyor ve renk sınıflandırmasından bağımsız.
 *
 * Değerler gerçek cihaz günlüklerinden alındı.
 */
class CardRenderedTest {

    private val ESIK = 217

    private fun analiz(
        tints: List<AnswerColorDetector.Tint>,
        vararg renkler: IntArray
    ) = AnswerColorDetector.Analysis(tints, null, renkler.toList())

    private fun notr(n: Int) = List(n) { AnswerColorDetector.Tint.NEUTRAL }

    @Test
    fun `oturmus beyaz kart hazir sayilir`() {
        val a = analiz(
            notr(4),
            intArrayOf(248, 248, 248), intArrayOf(248, 248, 248),
            intArrayOf(248, 248, 248), intArrayOf(248, 248, 248)
        )
        assertTrue(a.cardRendered(ESIK))
    }

    @Test
    fun `beliris animasyonunun mor karesi hazir sayilmaz`() {
        // Kartın solarak gelişi: 18:47:52'de günlüğe düşen gerçek kare.
        val a = analiz(
            notr(4),
            intArrayOf(56, 24, 152), intArrayOf(72, 40, 168),
            intArrayOf(72, 40, 184), intArrayOf(88, 56, 200)
        )
        assertFalse(a.cardRendered(ESIK))
    }

    @Test
    fun `gecis karesi yanlislikla yesil okunsa bile hazir sayilmaz`() {
        // Asıl hata buydu: eski kural "renkli şık var, demek kart hazır"
        // diyordu. Renk sınıflandırması zaten yanılmışken ona dayanmak,
        // kontrolü tümden işlevsiz bırakıyordu.
        val a = analiz(
            listOf(
                AnswerColorDetector.Tint.CORRECT, AnswerColorDetector.Tint.NEUTRAL,
                AnswerColorDetector.Tint.NEUTRAL, AnswerColorDetector.Tint.NEUTRAL
            ),
            intArrayOf(120, 136, 168), intArrayOf(72, 40, 168),
            intArrayOf(72, 40, 184), intArrayOf(88, 56, 200)
        )
        assertFalse(a.cardRendered(ESIK))
    }

    @Test
    fun `karar acildiginda koyulasan kirmizi sik kartu geri almaz`() {
        // Karar karesi: A yeşile dönüyor, B kırmızıya. B'nin baskın rengi
        // (184,152,168) eşiğin altında — ölçüm tek başına "kart hazır değil"
        // der. Bu yüzden servis bayrağı bir kez oturduktan sonra bir daha
        // sıfırlamıyor; test o karenin gerçekten eşiğin altında kaldığını
        // sabitliyor ki mantık yanlışlıkla geri çevrilmesin.
        val a = analiz(
            listOf(
                AnswerColorDetector.Tint.CORRECT, AnswerColorDetector.Tint.WRONG,
                AnswerColorDetector.Tint.NEUTRAL, AnswerColorDetector.Tint.NEUTRAL
            ),
            intArrayOf(200, 248, 200), intArrayOf(184, 152, 168),
            intArrayOf(248, 248, 248), intArrayOf(248, 248, 248)
        )
        assertFalse(a.cardRendered(ESIK))
    }

    @Test
    fun `renk olculemediyse hazir sayilmaz`() {
        // Kutular ölçülemeyecek kadar küçükse karar da verilemez.
        assertFalse(AnswerColorDetector.Analysis(notr(4)).cardRendered(ESIK))
    }
    @Test
    fun `sik yazili sikkin metin kutusu kart hazir sayilir`() {
        // "Hangisi Orta Çağ felsefesinin temel konularından değildir?"
        // sorusunun gerçek karesi. Şık kutuları ekrandan ölçülemediğinde
        // elimizdeki kutu OCR metninin sınırları; "Demokratikleşme"nin
        // kutusunda 128 örnekten 43'ü beyaz, 42'si lacivert. Kutu birkaç
        // piksel oynayınca baskın renk lacivert çıkıyor, kart "çizilmedi"
        // sayılıyor ve bot soruya hiç dokunmadan bekliyordu. Parlak örnek
        // oranı ise (%38-52) geçiş karelerinden (~%0) belirgin biçimde ayrı.
        val a = AnswerColorDetector.Analysis(
            notr(4), null,
            listOf(
                intArrayOf(248, 248, 248), intArrayOf(248, 248, 248),
                intArrayOf(248, 248, 248), intArrayOf(24, 24, 104)
            ),
            brights = listOf(0.77f, 0.73f, 0.77f, 0.38f)
        )
        assertTrue(a.cardRendered(ESIK))
    }

    @Test
    fun `gecis karesinde parlak ornek orani karti hazir yapmaz`() {
        // Solarak gelen kartta hap koyu mor; parlak örnek neredeyse yok.
        val a = AnswerColorDetector.Analysis(
            notr(4), null,
            listOf(
                intArrayOf(56, 24, 152), intArrayOf(72, 40, 168),
                intArrayOf(72, 40, 184), intArrayOf(88, 56, 200)
            ),
            brights = listOf(0f, 0f, 0.02f, 0.05f)
        )
        assertFalse(a.cardRendered(ESIK))
    }
}
