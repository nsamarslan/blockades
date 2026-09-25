package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OCR'ın bir iki harfi yanlış okuduğu soruların aynı kayda düşmesi.
 *
 * Değerler arşivden: "yıldıza" bir turda "yildza" okunmuş ve ikinci bir
 * kayıt açılmıştı. Benzerlik ölçüsü bunu zaten yakalıyor; sorun
 * karşılaştırma havuzunun tüm arşivi kapsamamasıydı. Bu testler ölçünün
 * eşiği ile gerçek bozulmalar arasındaki payı kayıt altına alıyor.
 */
class DedupTest {

    private fun sim(a: String, b: String) = TurkishText.similarity(a, b)

    @Test
    fun `harf dusmesi ayni soru sayilir`() {
        // Eşik %92; bu bozulmaların hepsi rahatça üstünde kalmalı.
        assertTrue(
            sim(
                "Enerjisi bitince sönen yildza ne ad verilir?",
                "Enerjisi bitince sönen yıldıza ne ad verilir?"
            ) >= 0.92f
        )
        assertTrue(
            sim(
                "Bilinen en eski usturlap kaçıIncı yüzyılda yapılmıştır?",
                "Bilinen en eski usturlap kaçıncı yüzyılda yapılmıştır?"
            ) >= 0.92f
        )
        assertTrue(
            sim("UFO kelimesinin aıliımı nedir?", "UFO kelimesinin açılımı nedir?") >= 0.92f
        )
    }

    @Test
    fun `farkli sorular birlesmez`() {
        // Aynı konuda, benzer kuruluşta ama başka sorular.
        assertTrue(
            sim(
                "Enerjisi bitince sönen yıldıza ne ad verilir?",
                "Enerjisi biten büyük yıldızların şiddetle patlamasına ne isim verilir?"
            ) < 0.92f
        )
        assertTrue(
            sim(
                "Dünya, Güneş sistemindeki kaçıncı gezegendir?",
                "Mars, Güneş sistemindeki kaçıncı gezegendir?"
            ) < 0.92f
        )
    }

    @Test
    fun `olumsuzluk farki her zaman ayirir`() {
        // Bu ikisi %90'ın üstünde benzeyebiliyor ama zıt sorular; onları
        // benzerlik değil olumsuzluk imzası ayırıyor.
        val a = "Hangisi felsefi düşüncenin özelliklerindendir?"
        val b = "Hangisi felsefi düşüncenin özelliklerinden değildir?"
        assertTrue(
            TurkishText.negationSignature(a) != TurkishText.negationSignature(b)
        )
    }
}
