package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sonu okunamamış soru, aynı sorunun kendisiyle birleşmeli — ama sadece o.
 *
 * Gerçek vaka: OCR "…kullanım amaçlarından biri değildir?" sorusunun son
 * kelimesini düşürdü ve arşive ikinci bir kayıt açıldı. Düşen kelime tam da
 * olumsuzluk kelimesi olduğu için, "zıt anlamlı sorular birleşmez" kuralı
 * birleşmeyi kesin olarak reddediyordu.
 *
 * Kuralı gevşetmenin bedeli ağır: yanlış birleşme iki ayrı sorunun cevabını
 * birden bozar. Bu yüzden ölçüt dar ve buradaki testler onu sabitliyor.
 * Karşı örnekler gerçek arşivden alındı.
 */
class TruncatedReadTest {

    private val MAX_MISSING_WORDS = 3
    private val MIN_HEAD_WORDS = 4
    private val WORD_MIN_SIMILARITY = 0.85f

    /** Repo.Probe.truncatedHead'in birebir karşılığı. */
    private fun truncatedHead(a: String, b: String): Boolean {
        val wa = TurkishText.words(a)
        val wb = TurkishText.words(b)
        val kisa = if (wa.size <= wb.size) wa else wb
        val uzun = if (wa.size <= wb.size) wb else wa
        val fazla = uzun.size - kisa.size
        if (fazla !in 1..MAX_MISSING_WORDS) return false
        if (kisa.size < MIN_HEAD_WORDS) return false
        return kisa.indices.all { i ->
            kisa[i] == uzun[i] ||
                TurkishText.similarity(kisa[i], uzun[i]) >= WORD_MIN_SIMILARITY
        }
    }

    private fun kirpik(kisa: String, uzun: String) = truncatedHead(kisa, uzun)

    @Test
    fun `son kelimesi dusen okuma ayni soru sayilir`() {
        // Günlükteki gerçek çift. OCR ortadaki bir harfi de kaçırmış
        // ("uydularin" / "uydularıin"), bu yüzden tam önek araması yetmiyor.
        assertTrue(
            kirpik(
                "Hangisi yapay uyduların kullanım amaçlarından biri",
                "Hangisi yapay uydularıin kullanım amaçlarından biri değildir?"
            )
        )
    }

    @Test
    fun `olumlu ve olumsuz soru birbirinin basi degildir`() {
        // Kuralın korumak zorunda olduğu asıl durum: aynı şıkları paylaşan
        // ama zıt anlamlı iki gerçek soru.
        assertFalse(
            kirpik(
                "Hangisi bilginin doğruluk ölçütlerindendir?",
                "Hangisi bilginin doğruluk ölçütlerinden değildir?"
            )
        )
    }

    @Test
    fun `bes kelimesi ortak ama farkli iki soru birlesmez`() {
        // Arşivden: beş kelimesi yan yana aynı, cevapları 5500 ve 460.
        assertFalse(
            kirpik(
                "Güneş'in yüzey sicaklığı yaklaşık kaç derecedir?",
                "Venüs gezegeninin yüzey sIcaklığı yaklaşık kaç derecedir?"
            )
        )
    }

    @Test
    fun `uc kelimesi dusen gercek arsiv cifti birlesir`() {
        // Arşivde duran gerçek kırpılmış okuma: üç kelime birden düşmüş.
        // Sınır ikide kalsaydı bu çift yakalanamazdı.
        assertTrue(
            kirpik(
                "Aşağıdaki ülkelerden hangisi Uluslararası Uzay İstasyonu",
                "Aşağıdaki ülkelerden hangisi Uluslararası Uzay istasyonu misyonu içerisinde değildir?"
            )
        )
    }

    @Test
    fun `yarisi okunmus metin birlesmez`() {
        // En fazla üç kelime düşebilir; dördü düşmüşse bu ayrı bir metindir.
        assertFalse(
            kirpik(
                "Hangisi yapay uyduların kullanım",
                "Hangisi yapay uyduların kullanım amaçlarından biri de değildir?"
            )
        )
    }

    @Test
    fun `cok kisa metinler hic degerlendirilmez`() {
        // Dört kelimeden kısa metinlerde tek kelimelik fark çok şey değiştirir.
        assertFalse(kirpik("Meteor nedir", "Meteor nedir peki"))
    }

    @Test
    fun `ayni uzunluktaki iki metin kirpik sayilmaz`() {
        assertFalse(
            kirpik(
                "Güneş'in en sıcak katmanı hangisidir?",
                "Güneş'in en soğuk katmanı hangisidir?"
            )
        )
    }

    // --- Şık kapısı: kural yalnız metne bakmıyor, şıklar da tutmalı --------

    @Test
    fun `tek harfi dusen sik ayni sik sayilir`() {
        // Arşivdeki gerçek çift: "Fransa" bir kayıtta "Frans" okunmuş.
        assertTrue(
            TurkishText.optionsNearlyMatch(
                listOf("Meksika", "Rusya", "Kanada", "Fransa"),
                listOf("Kanada", "Rusya", "Frans", "Meksika")
            )
        )
    }

    @Test
    fun `sayi siklarinda tek hane farki ayri cevaptir`() {
        // "82" ile "92" arasında bir harflik fark var ama bunlar farklı
        // cevaplar; harf toleransı sayılara uygulanmıyor.
        assertFalse(
            TurkishText.optionsNearlyMatch(
                listOf("82", "62", "92", "72"),
                listOf("83", "62", "92", "72")
            )
        )
    }

    @Test
    fun `kisa siklarda harf toleransi yok`() {
        // "Ay" / "Ad" gibi kısa şıklarda tek harf her şeyi değiştirir.
        assertFalse(
            TurkishText.optionsNearlyMatch(
                listOf("Ay", "Mars", "Venüs", "Dünya"),
                listOf("Ad", "Mars", "Venüs", "Dünya")
            )
        )
    }

    @Test
    fun `ayni sik iki kez eslesemez`() {
        // Bire-bir eşleştirme: soldaki iki "Rusya" sağdaki tek "Rusya"yı
        // paylaşamaz.
        assertFalse(
            TurkishText.optionsNearlyMatch(
                listOf("Rusya", "Rusya", "Kanada", "Fransa"),
                listOf("Rusya", "Meksika", "Kanada", "Fransa")
            )
        )
    }

    @Test
    fun `uc sikli listeler bu kapidan gecmez`() {
        assertFalse(
            TurkishText.optionsNearlyMatch(
                listOf("Rusya", "Kanada", "Fransa"),
                listOf("Rusya", "Kanada", "Fransa")
            )
        )
    }
}
