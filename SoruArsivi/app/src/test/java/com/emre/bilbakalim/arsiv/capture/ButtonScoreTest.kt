package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Tekrar Oyna" düğmesi tanıma kuralları.
 *
 * Yanlış eşleşme oyunu geciktiriyor ya da kapatıyor: bot "En Çok
 * Oynananlar" listesine basıp lobide dolaşmış, bir keresinde de "Tümünü
 * kapat"a basıp oyunu tamamen kapatmıştı. Değerler gerçek günlüklerden.
 */
class ButtonScoreTest {

    @Test
    fun `tekrar oyna birebir en yuksek puan`() {
        assertEquals(4, AutoPlayer.buttonScore("TEKRAR OYNA!"))
        assertEquals(4, AutoPlayer.buttonScore("Oyunu Başlat"))
    }

    @Test
    fun `basta ya da sonda gecen parca eslesir`() {
        assertTrue(AutoPlayer.buttonScore("Hemen Oyna") >= 3)
        assertTrue(AutoPlayer.buttonScore("Ana Menü Tekrar Oyna") >= 3)
        assertTrue(AutoPlayer.buttonScore("Oynamaya devam") >= 3)
    }

    @Test
    fun `lobi listesi basligi dugme sanilmaz`() {
        // 19:17:18  OTOMATİK: "En Çok Oynananlar" → yeni tur (3. kez)
        assertEquals(0, AutoPlayer.buttonScore("En Çok Oynananlar"))
        assertEquals(0, AutoPlayer.buttonScore("Son Oyunların"))
    }

    @Test
    fun `kapatma yazilari yalnizca birebir`() {
        assertEquals(2, AutoPlayer.buttonScore("Tamam"))
        assertEquals(0, AutoPlayer.buttonScore("Tümünü kapat"))
    }

    @Test
    fun `oyunu kapatabilecek yazilar hic eslesmez`() {
        assertEquals(0, AutoPlayer.buttonScore("Çıkış"))
        assertEquals(0, AutoPlayer.buttonScore("Hayır"))
        assertEquals(0, AutoPlayer.buttonScore("Vazgeç"))
    }
}
