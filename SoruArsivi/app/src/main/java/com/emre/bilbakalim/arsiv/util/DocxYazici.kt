package com.emre.bilbakalim.arsiv.util

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JPEG görsellerden Word belgesi (.docx) yazar.
 *
 * Kütüphane yok: .docx bir zip ve içindeki birkaç XML dosyası. Görseller
 * geldikçe zip'e yazılıyor; bellekte yalnızca belge gövdesinin XML'i
 * (görsel başına ~1 KB) tutuluyor.
 *
 * Bir soru iki sayfaya bölünmüyor: her soru tek bir satır içi görsel, Word
 * onu hiçbir zaman ortasından kesmiyor. İki sütunlu düzende sorular kenarlıksız
 * bir tablonun satırlarında duruyor ve satırlar "sayfada bölünmesin"
 * (cantSplit) işaretli. Sayfadan uzun görsel sayfaya sığacak kadar küçültülüyor.
 *
 * Android'e bağlı değil; birim testte denenebiliyor.
 */
class DocxYazici(hedef: OutputStream, private val sutun: Int = 2) : Closeable {

    private class Resim(val no: Int, val w: Int, val h: Int)

    private val zip = ZipOutputStream(BufferedOutputStream(hedef, 1 shl 16))
    private val govde = StringBuilder()
    private val iliskiler = StringBuilder()
    private val satir = ArrayList<Resim>(sutun)
    private var tabloAcik = false
    /** İki sütunlu düzende bir hücrenin genişliği, twip. */
    private val hucreW = ICERIK_W / sutun
    private var kapandi = false

    /** Yazılmış görsel sayısı. */
    var adet = 0
        private set

    init {
        // Sabit parçalar başta: bazı okuyucular [Content_Types].xml'i ilk sırada bekliyor.
        parca("[Content_Types].xml", ICERIK_TURLERI)
        parca("_rels/.rels", KOK_ILISKILER)
    }

    /** [jpeg] baytları, [w] x [h] piksel. */
    fun resimEkle(jpeg: ByteArray, w: Int, h: Int) {
        check(!kapandi) { "belge kapandı" }
        val no = ++adet
        zip.putNextEntry(ZipEntry("word/media/resim$no.jpeg"))
        zip.write(jpeg)
        zip.closeEntry()
        iliskiler.append(
            "<Relationship Id=\"rIdR$no\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                "Target=\"media/resim$no.jpeg\"/>"
        )
        satir += Resim(no, w, h)
        if (satir.size == sutun) satiriYaz()
    }

    private fun satiriYaz() {
        if (satir.isEmpty()) return
        if (sutun == 1) {
            for (r in satir) govde.append(paragraf(r, ICERIK_W, 120))
        } else {
            if (!tabloAcik) {
                govde.append(tabloBasi())
                tabloAcik = true
            }
            govde.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
            for (i in 0 until sutun) {
                govde.append("<w:tc><w:tcPr><w:tcW w:w=\"$hucreW\" w:type=\"dxa\"/></w:tcPr>")
                val r = satir.getOrNull(i)
                govde.append(if (r != null) paragraf(r, hucreW - 2 * HUCRE_YAN, 0) else "<w:p/>")
                govde.append("</w:tc>")
            }
            govde.append("</w:tr>")
        }
        satir.clear()
    }

    /** Tek görselli, ortalı paragraf; görsel [maxW] twip genişliğe sığdırılıyor. */
    private fun paragraf(r: Resim, maxW: Int, sonraBosluk: Int): String {
        val (wTwip, hTwip) = Sayfalayici.sigdir(r.w, r.h, maxW.toFloat(), ICERIK_H - 400f)
        val cx = (wTwip * EMU_TWIP).toLong()
        val cy = (hTwip * EMU_TWIP).toLong()
        return "<w:p><w:pPr><w:keepLines/><w:spacing w:before=\"0\" w:after=\"$sonraBosluk\"/>" +
            "<w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>" +
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"${r.no}\" name=\"Soru ${r.no}\"/>" +
            "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"${r.no}\" name=\"resim${r.no}.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rIdR${r.no}\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    private fun tabloBasi(): String = buildString {
        append("<w:tbl><w:tblPr><w:tblW w:w=\"$ICERIK_W\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>")
        append("<w:tblCellMar><w:top w:w=\"57\" w:type=\"dxa\"/><w:left w:w=\"$HUCRE_YAN\" w:type=\"dxa\"/>")
        append("<w:bottom w:w=\"170\" w:type=\"dxa\"/><w:right w:w=\"$HUCRE_YAN\" w:type=\"dxa\"/></w:tblCellMar>")
        append("</w:tblPr><w:tblGrid>")
        repeat(sutun) { append("<w:gridCol w:w=\"$hucreW\"/>") }
        append("</w:tblGrid>")
    }

    override fun close() {
        if (kapandi) return
        kapandi = true
        satiriYaz()
        // Tablo belgenin son öğesi olamıyor; ardından boş bir paragraf.
        if (tabloAcik) govde.append("</w:tbl><w:p/>")
        if (adet == 0) govde.append("<w:p/>")
        parca(
            "word/document.xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<w:body>$govde<w:sectPr><w:pgSz w:w=\"$SAYFA_W\" w:h=\"$SAYFA_H\"/>" +
                "<w:pgMar w:top=\"$KENAR\" w:right=\"$KENAR\" w:bottom=\"$KENAR\" w:left=\"$KENAR\" " +
                "w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
        )
        parca(
            "word/_rels/document.xml.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "$iliskiler</Relationships>"
        )
        zip.close()
    }

    private fun parca(ad: String, xml: String) {
        zip.putNextEntry(ZipEntry(ad))
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    companion object {
        /** A4 ve kenar boşluğu, twip (1/20 nokta). */
        private const val SAYFA_W = 11906
        private const val SAYFA_H = 16838
        private const val KENAR = 567
        private const val ICERIK_W = SAYFA_W - 2 * KENAR
        private const val ICERIK_H = SAYFA_H - 2 * KENAR
        private const val HUCRE_YAN = 57
        private const val EMU_TWIP = 635f

        private const val ICERIK_TURLERI =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
                "<Override PartName=\"/word/document.xml\" " +
                "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "</Types>"

        private const val KOK_ILISKILER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
                "Target=\"word/document.xml\"/></Relationships>"
    }
}
