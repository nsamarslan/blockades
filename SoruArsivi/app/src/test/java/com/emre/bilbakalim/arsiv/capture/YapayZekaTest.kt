package com.emre.bilbakalim.arsiv.capture

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YapayZekaTest {
    private val dort = listOf("İstanbul", "İzmir", "Ankara", "Bursa")

    @Test
    fun `tek harf cevap sik sirasina cevrilir`() {
        assertEquals(2, YapayZeka.harfiCoz("C", dort))
        assertEquals(0, YapayZeka.harfiCoz(" a\n", dort))
        assertEquals(1, YapayZeka.harfiCoz("**B**", dort))
        assertEquals(3, YapayZeka.harfiCoz("D)", dort))
        assertEquals(1, YapayZeka.harfiCoz("B.", dort))
    }

    @Test
    fun `susleme ve aciklama icindeki harf bulunur`() {
        assertEquals(2, YapayZeka.harfiCoz("Cevap: C", dort))
        assertEquals(2, YapayZeka.harfiCoz("C) Ankara", dort))
        assertEquals(1, YapayZeka.harfiCoz("Doğru şık B olmalı", dort))
    }

    @Test
    fun `sik sayisinin disindaki harf kabul edilmez`() {
        assertNull(YapayZeka.harfiCoz("D", listOf("Evet", "Hayır")))
        assertNull(YapayZeka.harfiCoz("E", dort))
        assertNull(YapayZeka.harfiCoz("", dort))
    }

    @Test
    fun `harf yoksa sik metni aranir`() {
        assertEquals(2, YapayZeka.harfiCoz("ankara", dort))
        assertNull(YapayZeka.harfiCoz("bilmiyorum", dort))
    }

    @Test
    fun `istem butun siklari harfle verir`() {
        val istem = YapayZeka.kullaniciIstemi("Başkent neresi?", dort)
        assertTrue(istem.contains("A) İstanbul"))
        assertTrue(istem.contains("D) Bursa"))
        assertTrue(istem.contains("A, B, C, D"))
        val iki = YapayZeka.kullaniciIstemi("Doğru mu?", listOf("Evet", "Hayır"))
        assertTrue(iki.contains("A, B") && !iki.contains("C"))
    }

    @Test
    fun `yanit metni iki saglayicidan da okunur`() {
        val groq = """{"choices":[{"message":{"role":"assistant","content":"B"}}]}"""
        assertEquals("B", YapayZeka.cevapMetni(YapayZeka.Saglayici.GROQ, groq))
        val gemini = """{"candidates":[{"content":{"parts":[{"text":"düşünce","thought":true},{"text":"C"}]}}]}"""
        assertEquals("C", YapayZeka.cevapMetni(YapayZeka.Saglayici.GEMINI, gemini))
        assertNull(YapayZeka.cevapMetni(YapayZeka.Saglayici.GEMINI, """{"candidates":[]}"""))
    }

    @Test
    fun `istek govdeleri gecerli json`() {
        val g = JSONObject(YapayZeka.groqGovdesi("openai/gpt-oss-120b", "Soru?", dort))
        assertEquals("openai/gpt-oss-120b", g.getString("model"))
        assertEquals(2, g.getJSONArray("messages").length())
        val m = JSONObject(YapayZeka.geminiGovdesi("gemini-3.5-flash-lite", "Soru?", dort))
        assertTrue(m.getJSONArray("contents").getJSONObject(0).toString().contains("C) Ankara"))
    }

    @Test
    fun `kota dolunca saglayici bir sure atlanir`() {
        assertEquals(60 * 60_000L, YapayZeka.cezaSuresiMs(429, "Rate limit reached on tokens per day (TPD)"))
        assertEquals(60_000L, YapayZeka.cezaSuresiMs(429, "rate limited"))
        assertEquals(30_000L, YapayZeka.cezaSuresiMs(429, "Please try again in 30.5s"))
        assertEquals(20_000L, YapayZeka.cezaSuresiMs(429, "body\nretry-after=3"))
        assertEquals(10 * 60_000L, YapayZeka.cezaSuresiMs(401, "invalid api key"))
        assertEquals(0L, YapayZeka.cezaSuresiMs(503, "overloaded"))
        assertEquals(0L, YapayZeka.cezaSuresiMs(-1, "timeout"))
    }

    @Test
    fun `sorular saglayicilar arasinda donusumlu dagitilir`() {
        val ikisi = listOf(YapayZeka.Saglayici.GROQ, YapayZeka.Saglayici.GEMINI)
        val ilk = YapayZeka.siralama(ikisi, 0)
        val ikinci = YapayZeka.siralama(ikisi, 1)
        assertEquals(YapayZeka.Saglayici.GROQ, ilk[0].first)
        assertEquals(YapayZeka.Saglayici.GEMINI, ikinci[0].first)
        assertEquals(YapayZeka.Saglayici.GROQ, YapayZeka.siralama(ikisi, 2)[0].first)
        // Asıl model düşerse önce öteki sağlayıcıya, yedek modellere sonra.
        assertEquals(YapayZeka.Saglayici.GEMINI, ilk[1].first)
        assertEquals(YapayZeka.modeller(YapayZeka.Saglayici.GROQ)[1], ilk[2].second)
        assertEquals(4, ilk.size)
        // Tek anahtar girildiyse hep o.
        val tek = YapayZeka.siralama(listOf(YapayZeka.Saglayici.GEMINI), 5)
        assertTrue(tek.all { it.first == YapayZeka.Saglayici.GEMINI })
        assertTrue(YapayZeka.siralama(emptyList(), 3).isEmpty())
    }

    @Test
    fun `groq kota basliklari okunur`() {
        assertEquals(2_100L, YapayZeka.sureCoz("2.1s"))
        assertEquals(86_400L, YapayZeka.sureCoz("1m26.4s"))
        assertEquals(7L, YapayZeka.sureCoz("7.66ms"))
        assertEquals(3_723_000L, YapayZeka.sureCoz("1h2m3s"))
        assertNull(YapayZeka.sureCoz("yarın"))
        // Kota bol: kenara alınmaz.
        assertEquals(0L, YapayZeka.onlemSuresiMs("999", "1m26.4s", "7720", "2.1s"))
        // Dakikalık token bitmek üzere: sıfırlanana kadar.
        assertEquals(30_000L, YapayZeka.onlemSuresiMs("500", "1m", "300", "30s"))
        // Günlük istek bitti.
        assertEquals(3_600_000L, YapayZeka.onlemSuresiMs("0", "1h", "7000", "1s"))
        // Gemini başlık göndermiyor.
        assertEquals(0L, YapayZeka.onlemSuresiMs(null, null, null, null))
    }
}
