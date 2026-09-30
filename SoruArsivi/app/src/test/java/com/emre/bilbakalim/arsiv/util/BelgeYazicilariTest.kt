package com.emre.bilbakalim.arsiv.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dışa aktarımın PDF ve Word yazıcıları.
 *
 * Asıl şart: bir soru iki sayfaya bölünmemeli. Sayfa yerleşimi bunu
 * garanti ediyor; PDF'in iç yapısı (ofset tablosu) da okuyucuların açabileceği
 * biçimde olmalı.
 */
class BelgeYazicilariTest {

    /** Yapı testleri JPEG'i çözmüyor; içerik önemsiz. */
    private fun jpeg(@Suppress("UNUSED_PARAMETER") w: Int, @Suppress("UNUSED_PARAMETER") h: Int) =
        ByteArray(2048) { (it * 31).toByte() }

    /** JPEG'in genişlik ve yüksekliği, SOF başlığından. */
    private fun jpegBoyutu(b: ByteArray): Pair<Int, Int> {
        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) { i++; continue }
            val m = b[i + 1].toInt() and 0xFF
            val uzunluk = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (m in 0xC0..0xC3) {
                val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                return w to h
            }
            i += 2 + uzunluk
        }
        error("JPEG boyutu bulunamadı")
    }

    @Test
    fun `hicbir soru sayfa sinirini asmaz`() {
        val d = Sayfalayici(Sayfalayici.A4_W, Sayfalayici.A4_H, 24f, 12f, 2)
        val yerler = (0 until 40).chunked(2).flatMap { satir ->
            d.satir(satir.map { if (it % 7 == 0) 820 to 1400 else 820 to 700 })
        }
        assertEquals(40, yerler.size)
        for (y in yerler) {
            assertTrue("üstten taşıyor: $y", y.y >= 24f - 0.01f)
            assertTrue("alttan taşıyor: $y", y.y + y.h <= Sayfalayici.A4_H - 24f + 0.01f)
        }
        // Sıra korunuyor: sayfa numarası hiç geri gitmiyor.
        assertTrue(yerler.zipWithNext().all { (a, b) -> b.sayfa >= a.sayfa })
        // Satırdaki iki görsel aynı sayfada, yan yana.
        assertEquals(yerler[0].sayfa, yerler[1].sayfa)
        assertTrue(yerler[1].x > yerler[0].x)
    }

    @Test
    fun `sayfadan uzun gorsel sayfaya sigacak kadar kuculur`() {
        val d = Sayfalayici(Sayfalayici.A4_W, Sayfalayici.A4_H, 24f, 12f, 1)
        val (y) = d.satir(listOf(800 to 5000))
        assertTrue(y.h <= Sayfalayici.A4_H - 48f + 0.01f)
        assertEquals(800f / 5000f, y.w / y.h, 0.001f)
    }

    @Test
    fun `pdf okunabilir yapida ve her gorsel gomulu`() {
        val bayt = ByteArrayOutputStream()
        PdfYazici(bayt).use { pdf -> repeat(7) { pdf.resimEkle(jpeg(820, 700), 820, 700) } }
        val metin = String(bayt.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(metin.startsWith("%PDF-1.4"))
        assertTrue(metin.trimEnd().endsWith("%%EOF"))
        assertEquals(7, Regex("/Subtype /Image").findAll(metin).count())

        // Ofset tablosundaki her kayıt gerçekten o nesnenin başını gösteriyor.
        val xref = metin.substringAfterLast("startxref\n").lines().first().trim().toInt()
        val satirlar = metin.substring(xref).lines()
        val adet = satirlar[1].split(" ")[1].toInt()
        for (id in 1 until adet) {
            val ofset = satirlar[2 + id].substring(0, 10).toInt()
            assertTrue("nesne $id", metin.startsWith("$id 0 obj", ofset))
        }
        // 2 sütun, satır başına ~229 nokta: sayfada 3 satır, 7 görsel 2 sayfa.
        assertTrue(metin.contains("/Count 2"))
    }

    @Test
    fun `docx gecerli xml ve satirlar bolunmez`() {
        val bayt = ByteArrayOutputStream()
        DocxYazici(bayt).use { doc -> repeat(3) { doc.resimEkle(jpeg(820, 700), 820, 700) } }
        val parcalar = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bayt.toByteArray())).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                parcalar[e.name] = z.readBytes()
            }
        }
        assertTrue(parcalar.keys.containsAll(listOf(
            "[Content_Types].xml", "_rels/.rels", "word/document.xml",
            "word/_rels/document.xml.rels", "word/media/resim1.jpeg", "word/media/resim3.jpeg"
        )))
        val fabrika = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (ad in parcalar.keys.filter { it.endsWith(".xml") || it.endsWith(".rels") }) {
            fabrika.newDocumentBuilder().parse(ByteArrayInputStream(parcalar.getValue(ad)))
        }
        val govde = String(parcalar.getValue("word/document.xml"), Charsets.UTF_8)
        assertEquals(3, Regex("<w:drawing>").findAll(govde).count())
        // Üç görsel iki satır; her satır sayfada bölünmesin diye işaretli.
        assertEquals(2, Regex("<w:cantSplit/>").findAll(govde).count())
    }

    /**
     * Elle bakmak için örnek çıktı: SORU_ARSIVI_ORNEK ortam değişkeni bir
     * klasörü gösteriyorsa oraya ornek.pdf ve ornek.docx yazılır.
     */
    @Test
    fun `ornek cikti`() {
        val klasor = System.getenv("SORU_ARSIVI_ORNEK") ?: return
        val gorseller = File(klasor).listFiles { f -> f.name.endsWith(".jpg") }?.sorted().orEmpty()
            .map { f -> f.readBytes().let { it to jpegBoyutu(it) } }
        if (gorseller.isEmpty()) return
        File(klasor, "ornek.pdf").outputStream().use { o ->
            PdfYazici(o).use { pdf -> gorseller.forEach { (b, wh) -> pdf.resimEkle(b, wh.first, wh.second) } }
        }
        File(klasor, "ornek.docx").outputStream().use { o ->
            DocxYazici(o).use { doc -> gorseller.forEach { (b, wh) -> doc.resimEkle(b, wh.first, wh.second) } }
        }
    }
}
