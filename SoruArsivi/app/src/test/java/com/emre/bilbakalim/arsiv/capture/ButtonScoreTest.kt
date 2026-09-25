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

    @Test
    fun `tek harfi yanlis okunan uzun dugme yazisi taninir`() {
        // Gerçek günlük: tur sonu ekranında "Tekrar Oyna" bir dakika boyunca
        // "Tekrar Oynd" okundu ve hiçbir şeye basılmadı.
        assertTrue(AutoPlayer.buttonScore("Tekrar Oynd") >= 3)
        assertTrue(AutoPlayer.buttonScore("Tekrar 0yna") >= 3)
    }

    @Test
    fun `kisa yazilarda harf hatasi hos gorulmez`() {
        // Kısa yazıda tek harf başka bir kelime demek.
        assertEquals(0, AutoPlayer.buttonScore("Oyma"))
        assertEquals(0, AutoPlayer.buttonScore("Başka"))
    }

    @Test
    fun `can doldurma dugmesi tur baslatma sayilmaz`() {
        // "Doldur" yalnızca "Can Kalmadı" penceresinde, ayar açıksa basılıyor;
        // sıradan bir düğme gibi puan almamalı.
        assertEquals(0, AutoPlayer.buttonScore("Doldur"))
        assertEquals(0, AutoPlayer.buttonScore("+1 Can"))
    }

    @Test
    fun `yan yana dugme yazilari genis bosluktan bolunur`() {
        // Tur sonu, 1080 px: "Kategoriler | Ana Menü | Tekrar Oyna" tek satır.
        val sol = intArrayOf(95, 440, 520, 790, 915)
        val sag = intArrayOf(290, 505, 640, 900, 1015)
        val yuk = intArrayOf(40, 40, 40, 40, 40)
        assertEquals(listOf(0..0, 1..2, 3..4), AutoPlayer.bosluklaBol(sol, sag, yuk))
    }

    @Test
    fun `normal cumle bolunmez`() {
        val sol = intArrayOf(100, 215, 330)
        val sag = intArrayOf(200, 315, 430)
        val yuk = intArrayOf(40, 40, 40)
        assertEquals(listOf(0..2), AutoPlayer.bosluklaBol(sol, sag, yuk))
    }
}
