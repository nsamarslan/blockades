package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tekrar denetiminin testleri.
 *
 * Denetim, her yeni okumayı arşivin tamamıyla karşılaştırıyor; hızlanması
 * için satırlar önceden hesaplanıyor ve sonucu değiştiremeyecek hesaplar
 * atlanıyor. Bu testler kuralların kendisini sabitliyor: hızlandırma hiçbir
 * kararı değiştirmemeli.
 */
class TekrarSorgusuTest {

    private fun ayni(q1: String, o1: List<String>, q2: String, o2: List<String>) =
        TekrarSorgusu(q2, o2).matches(TekrarAdayi(1L, q1, o1))

    private val siklar = listOf("Kuyruklu yıldız", "Göktaşı", "Kara delik", "Beyaz cüce")

    @Test
    fun `harf dusmesi ayni soru sayilir`() {
        assertTrue(
            ayni(
                "Enerjisi bitince sönen yıldıza ne ad verilir?", siklar,
                "Enerjisi bitince sönen yildza ne ad verilir?", siklar
            )
        )
    }

    @Test
    fun `olumsuzluk farki ayirir`() {
        assertFalse(
            ayni(
                "Hangisi bilim insanının özelliklerindendir?", siklar,
                "Hangisi bilim insanının özelliklerinden değildir?", siklar
            )
        )
    }

    @Test
    fun `sonu okunamamis soru ayni soru sayilir`() {
        // Düşen kelime olumsuzluk kelimesi olsa bile: şıklar birebir aynı.
        assertTrue(
            ayni(
                "Hangisi bu aletin kullanım amaçlarından biri değildir?", siklar,
                "Hangisi bu aletin kullanım amaçlarından biri", siklar
            )
        )
    }

    @Test
    fun `sik ayni metin cok farkli ise ayri soru`() {
        assertFalse(
            ayni(
                "Güneş sistemindeki en büyük gezegen hangisidir?", siklar,
                "Işık yılı neyin ölçü birimidir?", siklar
            )
        )
    }

    @Test
    fun `basina fazlalik yapismis okuma ayni soru sayilir`() {
        // Kapsama kuralı: "17. Süre Bitti" gibi bir fazlalık, şıklar aynı.
        assertTrue(
            ayni(
                "Güneş sistemindeki en büyük gezegen hangisidir?", siklar,
                "Süre Bitti Güneş sistemindeki en büyük gezegen hangisidir?", siklar
            )
        )
    }

    @Test
    fun `uzunlugu cok farkli metinler karsilastirilmadan elenir`() {
        assertFalse(
            ayni(
                "Hangisi?", listOf("A", "B", "C", "D"),
                "Güneş sistemindeki en büyük gezegen hangisidir?", siklar
            )
        )
    }

    // --- Şıkları farklı, metni çok benzer sorular -------------------------

    private val gelme = "Hilesiz bir zar atıldığında 3 gelme olasılığı kaçtır?"
    private val gelmeme = "Hilesiz bir zar atıldığında 3 gelmeme olasılığı kaçtır?"

    @Test
    fun `metni benzeyen ama siklari farkli soru ayri kayit`() {
        // Gerçek günlük (3.6): iki soru tek kayıt olmuş, "gelme" sorusunun
        // cevabı 1/6, "gelmeme" sorusunun doğru cevabı 5/6'nın üstüne yazılmıştı.
        assertFalse(
            ayni(
                gelmeme, listOf("3/4", "4/2", "1/6", "5/6"),
                gelme, listOf("1/6", "2/4", "3/6", "1/3")
            )
        )
    }

    @Test
    fun `sikleri ayni olsa da olumsuzluk eki ayirir`() {
        val s = listOf("1/6", "5/6", "2/6", "3/6")
        assertFalse(ayni(gelmeme, s, gelme, s))
        assertFalse(
            ayni(
                "Hangi hayvan kış uykusuna yatmaz?", siklar,
                "Hangi hayvan kış uykusuna yatar?", siklar
            )
        )
    }

    @Test
    fun `bir sikki bozuk okunan ayni soru yine ayni kayit`() {
        // OCR harf hatası hem metinde hem bir şıkta: şıklardan biri tutmayabilir.
        assertTrue(
            ayni(
                "Enerjisi bitince sönen yıldıza ne ad verilir?", siklar,
                "Enerjisi bitince sönen yildza ne ad verilir?",
                listOf("Kuyruklu yıldız", "Göktaşı", "Kara delik", "Bcyaz cüe")
            )
        )
    }

    @Test
    fun `iki sikki tutmayan benzer metin ayri soru`() {
        assertFalse(
            ayni(
                "Enerjisi bitince sönen yıldıza ne ad verilir?", siklar,
                "Enerjisi bitince sönen yildza ne ad verilir?",
                listOf("Kuyruklu yıldız", "Göktaşı", "Nötron yıldızı", "Kızıl dev")
            )
        )
    }

    @Test
    fun `sik uyumu kurali`() {
        fun uyar(a: List<String>, b: List<String>) = TekrarSorgusu.siklarUyusuyor(
            a.map { com.emre.bilbakalim.arsiv.util.TurkishText.normalizeKey(it) },
            b.map { com.emre.bilbakalim.arsiv.util.TurkishText.normalizeKey(it) }
        )
        assertTrue(uyar(listOf("A1", "B2", "C3", "D4"), listOf("D4", "C3", "B2", "A1")))
        assertTrue(uyar(listOf("A1", "B2", "C3", "D4"), listOf("D4", "C3", "B2", "X9")))
        assertFalse(uyar(listOf("A1", "B2", "C3", "D4"), listOf("D4", "C3", "Y8", "X9")))
        // Eksik şıkla açılmış kayıt: kısa listenin biri tutmayabilir.
        assertTrue(uyar(listOf("A1", "B2", "C3"), listOf("A1", "B2", "Z0", "D4")))
        // İki şıklık listede ikisi de tutmalı.
        assertFalse(uyar(listOf("A1", "B2"), listOf("A1", "Z0", "C3", "D4")))
    }

    // --- Şıkları aynı, tek kelimesi ya da sayısı farklı kalıp sorular ------

    private val trig = listOf("Kosinüs", "Kotanjant", "Sinüs", "Tanjant")

    @Test
    fun `kisa kelimesi farkli kalip sorular ayri kayit`() {
        // Gerçek günlük (3.7): üçü de #2913'e düşüyor, bot her seferinde
        // öbürünün cevabına basıyordu.
        val cos = "\"cos\" şeklinde ifade edilen trigonometrik işlev aşağıdakilerden hangisidir?"
        val cot = "\"cot\" şeklinde ifade edilen trigonometrik işlev aşağıdakilerden hangisidir?"
        val tan = "\"tan\" şeklinde ifade edilen trigonometrik işlev aşağıdakilerden hangisidir?"
        assertFalse(ayni(cos, trig, cot, trig.reversed()))
        assertFalse(ayni(cos, trig, tan, trig))
        assertFalse(ayni(cot, trig, tan, trig))
        val semboller = listOf("V", "V", "V", "<")
        assertFalse(
            ayni(
                "Matematikte \"veya\" bağlacı hangi sembolle gösterilir?", semboller,
                "Matematikte \"ve\" bağlacı hangi sembolle gösterilir?", semboller
            )
        )
    }

    @Test
    fun `sayisi farkli kalip sorular ayri kayit`() {
        val ucgen = listOf("Çeşitkenar Üçgen", "İkizkenar Üçgen", "Eşkenar Üçgen", "Dik üçgen")
        assertFalse(
            ayni(
                "Bir üçgenin iki açısı 60 ve 30 ise, bu üçgen nasıl bir üçgendir?", ucgen,
                "Bir üçgenin iki açISI 75 ve 30 ise, bu üçgen nasıl bir üçgendir?", ucgen
            )
        )
        val sonuc = listOf("166", "179", "159", "189")
        assertFalse(ayni("12x14+11 işleminin sonucu kaçtır?", sonuc, "11x14+12 işleminin sonucu kaçtır?", sonuc))
        assertFalse(
            ayni(
                "Tam sayılarda toplama işleminin etkisiz elemanı ile 2 sayısının toplamı kaçtır?",
                listOf("0", "1", "2", "3"),
                "Tam sayılarda çarpma işleminin etkisiz elemanı ile 1 sayısının toplamı kaçtır?",
                listOf("0", "1", "2", "3")
            )
        )
    }

    @Test
    fun `ocr harf hatasi ve bosluk farki ayni kayit`() {
        val s = listOf("1/36", "1/6", "1/12", "1/18")
        // Rakamla karışan harf: "%2O" / "%20", "1l" / "11".
        assertTrue(ayni("Fiyatı %20 artan ürün kaç TL olur?", s, "Fiyatı %2O artan ürün kaç TL olur?", s))
        assertTrue(ayni("11'den 100'e kadar kaç asal sayı vardır?", s, "1l'den 100'e kadar kaç asal sayı vardır?", s))
        // Boşluğu farklı okunmuş metin.
        assertTrue(
            ayni(
                "Aynı anda atılan hilesiz iki zarın da 1 gelme olasılığı kaçtır?", s,
                "Aynı anda atılan hilesiz iki zarın da 1gelme olasılığı kaçtır?", s
            )
        )
        // Uzun kelimede iki harf.
        assertTrue(
            ayni(
                "Bilinen en eski usturlap kaçıncı yüzyılda yapılmıştır?", s,
                "Bilinen en eski usturlap kaçıIncı yüzyılda yapılmıştır?", s
            )
        )
    }

    @Test
    fun `ortada fazladan kelime ayri soru`() {
        assertFalse(
            ayni(
                "Güneş sistemindeki en büyük gezegen hangisidir?", siklar,
                "Güneş sistemindeki en büyük ikinci gezegen hangisidir?", siklar
            )
        )
    }

    @Test
    fun `baska sorunun metni yazilmis kayit taninir`() {
        // Parmak izi tutan kaydın metni başka bir sorununsa geri alınıyor.
        assertTrue(
            TekrarSorgusu.ayriMetinler(
                "\"tan\" şeklinde ifade edilen trigonometrik işlev aşağıdakilerden hangisidir?",
                "\"cot\" şeklinde ifade edilen trigonometrik işlev aşağıdakilerden hangisidir?"
            )
        )
        assertFalse(
            TekrarSorgusu.ayriMetinler(
                "Enerjisi bitince sönen yıldıza ne ad verilir?",
                "Enerjisi bitince sönen yildza ne ad verilir?"
            )
        )
    }
}
