package com.emre.bilbakalim.arsiv.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teşhis izinin testleri.
 *
 * İz, "bot neden bekledi" sorusunu günlükten doğrudan cevaplamak için var:
 * her taramanın nerede bittiği ve hangi aşamanın ne kadar sürdüğü. Asıl
 * korunması gereken iki şey: sessiz bir döngü görünür olmalı (ama günlüğü
 * boğmamalı), ve takılan bir tarama dönmese de yazılmalı.
 */
class TaramaIziTest {

    private var saat = 1_000L
    private val satirlar = ArrayList<String>()
    private val izi = TaramaIzi({ saat }) { satirlar.add(it) }

    /** Bir tarama: [sure] ms sürer, [cikis]'tan biter. */
    private fun tara(cikis: String, sure: Long = 50L, oto: String = "-", asama: String = "ocr") {
        val iz = izi.basla()
        iz.adim(asama)
        saat += sure
        iz.cik(cikis)
        iz.oto = oto
        izi.bitir(iz)
        saat += 300L
    }

    @Test
    fun `ayni yerden cikan taramalar tek satirda toplanir`() {
        // Asıl arızanın izi: soru her taramada okunup kart kapısında geri
        // çevriliyordu ve günlükte tek satır yoktu.
        repeat(5) { tara("kart_kapisi") }
        assertTrue(satirlar.isEmpty())
        izi.bosalt()
        assertEquals(1, satirlar.size)
        assertTrue(satirlar[0], satirlar[0].contains("[kart_kapisi/-×5]"))
        assertTrue(satirlar[0], satirlar[0].contains("ocr=50"))
    }

    @Test
    fun `gidip gelen cikislar tek satirda sayilir`() {
        // Taramalar sık sık iki çıkış arasında gidip geliyor; her geçişte
        // satır yazılsaydı günlük boğulurdu.
        repeat(3) {
            tara("veri_yok")
            tara("ayni_soru", oto = "gecikme")
        }
        assertTrue(satirlar.isEmpty())
        izi.bosalt()
        assertEquals(1, satirlar.size)
        assertTrue(satirlar[0], satirlar[0].contains("veri_yok/-×3 ayni_soru/gecikme×3"))
    }

    @Test
    fun `uzun suren sessiz dongu ara ara yazilir`() {
        // 20 saniyelik bir takılma dört-beş satır bırakmalı, sıfır değil.
        repeat(60) { tara("kart_kapisi") }
        assertTrue(satirlar.size in 3..6)
    }

    @Test
    fun `yavas tarama asama asama yazilir`() {
        tara("tekrar", sure = TaramaIzi.YAVAS_MS + 100, asama = "db")
        assertTrue(satirlar.single().startsWith("İZ YAVAŞ"))
        assertTrue(satirlar.single().contains("db=${TaramaIzi.YAVAS_MS + 100}"))
    }

    @Test
    fun `taramalar arasi bosluk yazilir`() {
        tara("durgun")
        saat += TaramaIzi.BOSLUK_MS + 1
        tara("durgun")
        assertTrue(satirlar.any { it.startsWith("İZ BOŞLUK") })
    }

    @Test
    fun `donmeyen tarama nerede kaldigiyla yazilir`() {
        val iz = izi.basla()
        iz.adim("onplan")
        saat += 20L
        iz.adim("ocr")
        saat += TaramaIzi.TAKILMA_MS
        izi.denetle()
        izi.denetle()
        val takildi = satirlar.filter { it.startsWith("İZ TAKILDI") }
        assertEquals("bir kez yazılmalı", 1, takildi.size)
        assertTrue(takildi[0], takildi[0].contains("aşama=ocr"))
    }

    @Test
    fun `karar turu sirasindaki taramalar yazilmaz`() {
        repeat(4) { tara("burst") }
        izi.bosalt()
        assertTrue(satirlar.isEmpty())
    }
}
