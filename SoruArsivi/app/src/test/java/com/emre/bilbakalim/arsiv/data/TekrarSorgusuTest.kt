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
}
