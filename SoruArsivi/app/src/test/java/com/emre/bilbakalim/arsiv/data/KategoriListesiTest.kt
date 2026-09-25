package com.emre.bilbakalim.arsiv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ana ekrandaki kategori listesi: elle ekleme ve ad değiştirme.
 *
 * Asıl istek: "Din Kültürü"nü "Bilim" yaparsam o kategorideki bütün sorular
 * "Bilim" olmalı. Kayıtların taşınması veritabanında; burada listenin, eski
 * adların ve ekrandan ad tanımanın aynı kararı vermesi deneniyor.
 */
class KategoriListesiTest {

    private val varsayilan = KategoriListesi(listOf("Felsefe", "Tarih", "Din Kültürü", "Spor"))

    @Test
    fun `ekle listenin sonuna koyar`() {
        val l = varsayilan.ekle("  Yapay   Zeka ")
        assertEquals(listOf("Felsefe", "Tarih", "Din Kültürü", "Spor", "Yapay Zeka"), l.adlar)
    }

    @Test
    fun `ayni ad farkli yazilisla ikinci kez eklenmez`() {
        assertSame(varsayilan, varsayilan.ekle("DİN KÜLTÜRÜ"))
        assertSame(varsayilan, varsayilan.ekle("tarih"))
        assertSame(varsayilan, varsayilan.ekle("   "))
        assertEquals("Din Kültürü", varsayilan.bul("din kulturu"))
    }

    @Test
    fun `ad degisince yeri korunur ve eski ad yeni ada yonlenir`() {
        val d = varsayilan.yenidenAdlandir("Din Kültürü", "Bilim")!!
        assertEquals("Bilim", d.hedef)
        assertFalse(d.birlesti)
        assertEquals(listOf("Felsefe", "Tarih", "Bilim", "Spor"), d.liste.adlar)
        // Oyun ekranda hâlâ eski adı gösteriyor; tanıma yeni adı vermeli.
        assertEquals("Bilim", d.liste.tani("DİN KÜLTÜRÜ"))
        assertEquals("Bilim", d.liste.tani("Bilim"))
        // Eski yedekten gelen kayıt da yeni ada geçmeli.
        assertEquals("Bilim", d.liste.guncelAd("Din Kültürü"))
    }

    @Test
    fun `var olan bir ada cevrilince iki kategori birlesir`() {
        val d = varsayilan.yenidenAdlandir("Din Kültürü", "tarih")!!
        assertEquals("Tarih", d.hedef)
        assertTrue(d.birlesti)
        assertEquals(listOf("Felsefe", "Tarih", "Spor"), d.liste.adlar)
        assertEquals("Tarih", d.liste.tani("Din Kültürü"))
    }

    @Test
    fun `yalnizca yazilisi degisirse birlesme sayilmaz`() {
        val d = varsayilan.yenidenAdlandir("Spor", "SPOR")!!
        assertEquals("SPOR", d.hedef)
        assertFalse(d.birlesti)
        assertEquals(listOf("Felsefe", "Tarih", "Din Kültürü", "SPOR"), d.liste.adlar)
        assertTrue(d.liste.eskiAdlar.isEmpty())
        assertEquals("SPOR", d.liste.tani("spor"))
    }

    @Test
    fun `art arda ad degisiminde en eski ad da son ada yonlenir`() {
        val bir = varsayilan.yenidenAdlandir("Din Kültürü", "Bilim")!!.liste
        val iki = bir.yenidenAdlandir("Bilim", "Fen Bilimleri")!!.liste
        assertEquals(listOf("Felsefe", "Tarih", "Fen Bilimleri", "Spor"), iki.adlar)
        assertEquals("Fen Bilimleri", iki.tani("Din Kültürü"))
        assertEquals("Fen Bilimleri", iki.tani("Bilim"))
    }

    @Test
    fun `eski ad yeniden eklenirse ayri kategori olur`() {
        val l = varsayilan.yenidenAdlandir("Din Kültürü", "Bilim")!!.liste.ekle("Din Kültürü")
        assertEquals(listOf("Felsefe", "Tarih", "Bilim", "Spor", "Din Kültürü"), l.adlar)
        assertEquals("Din Kültürü", l.tani("Din Kültürü"))
        assertEquals("Bilim", l.tani("Bilim"))
    }

    @Test
    fun `yonlenen bir ad baska kategoriye verilirse yonlendirme kalkar`() {
        // Din Kültürü → Bilim, sonra Spor → "Din Kültürü".
        val l = varsayilan.yenidenAdlandir("Din Kültürü", "Bilim")!!.liste
            .yenidenAdlandir("Spor", "Din Kültürü")!!.liste
        assertEquals(listOf("Felsefe", "Tarih", "Bilim", "Din Kültürü"), l.adlar)
        assertEquals("Din Kültürü", l.tani("Din Kültürü"))
        assertEquals("Din Kültürü", l.tani("Spor"))
    }

    @Test
    fun `listede olmayan arsiv kategorisi yeni adla listeye girer`() {
        // Elle yazılmış ya da yedekten gelmiş bir kategori.
        val d = varsayilan.yenidenAdlandir("genel kultur", "Genel Kültür")!!
        assertEquals("Genel Kültür", d.hedef)
        assertEquals(listOf("Felsefe", "Tarih", "Din Kültürü", "Spor", "Genel Kültür"), d.liste.adlar)
    }

    @Test
    fun `bos ya da degismeyen ad reddedilir`() {
        assertNull(varsayilan.yenidenAdlandir("Tarih", "   "))
        assertNull(varsayilan.yenidenAdlandir("Tarih", " Tarih "))
        assertNull(varsayilan.yenidenAdlandir("", "Bilim"))
    }

    @Test
    fun `tanima yalnizca yazinin tamamina bakar`() {
        assertNull(varsayilan.tani("Tarih boyunca en uzun süren savaş hangisidir?"))
        assertNull(varsayilan.tani(""))
        assertEquals("Felsefe", varsayilan.tani("FELSEFE"))
    }

    @Test
    fun `guncel ad listedeki yazilisi verir, bilinmeyeni oldugu gibi birakir`() {
        assertEquals("Tarih", varsayilan.guncelAd("  tarih "))
        assertEquals("Astronomi", varsayilan.guncelAd("Astronomi"))
        assertNull(varsayilan.guncelAd(null))
        assertNull(varsayilan.guncelAd("  "))
    }
}
