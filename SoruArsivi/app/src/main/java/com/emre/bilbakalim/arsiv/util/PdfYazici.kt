package com.emre.bilbakalim.arsiv.util

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * JPEG görsellerden PDF yazar; görseller sayfalara [Sayfalayici] ile
 * diziliyor, bir soru asla iki sayfaya bölünmüyor.
 *
 * Neden Android'in PdfDocument'ı değil: o, çizilen her görseli sıkıştırılmamış
 * piksel olarak yeniden kodluyor. Yüzlerce soruluk bir dışa aktarım yüzlerce
 * megabayt tutuyor ve yavaşlıyordu. Burada JPEG baytları olduğu gibi
 * gömülüyor (DCTDecode): dosya görsellerin toplamı kadar, yazması anlık.
 *
 * Görseller geldikçe diske yazılıyor; bellekte yalnızca bir satır ve bir
 * sayfanın yerleşimi tutuluyor. Metin yok, yazı tipi gerekmiyor: sorunun
 * başlığı ve doğru cevap görselin içine çizilmiş olarak geliyor.
 *
 * Android'e bağlı değil; birim testte denenebiliyor.
 */
class PdfYazici(hedef: OutputStream, private val sutun: Int = 2) : Closeable {

    private class Resim(val id: Int, val w: Int, val h: Int)

    private val out = SayanAkis(BufferedOutputStream(hedef, 1 shl 16))
    private val ofsetler = HashMap<Int, Long>()
    /** 1: katalog, 2: sayfa ağacı; ikisi en sonda yazılıyor. */
    private var sonId = 2
    private val sayfalar = ArrayList<Int>()
    private val duzen = Sayfalayici(
        Sayfalayici.A4_W, Sayfalayici.A4_H, KENAR, ARALIK, sutun
    )
    private val satir = ArrayList<Resim>(sutun)
    private val sayfaResimleri = ArrayList<Pair<Resim, Sayfalayici.Yer>>()
    private var acikSayfa = 0
    private var kapandi = false

    /** Yazılmış görsel sayısı. */
    var adet = 0
        private set

    init {
        yaz("%PDF-1.4\n")
        // İkili dosya olduğunu okuyuculara bildiren yorum satırı.
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
    }

    /** [jpeg] baytları, [w] x [h] piksel, RGB. */
    fun resimEkle(jpeg: ByteArray, w: Int, h: Int) {
        check(!kapandi) { "PDF kapandı" }
        val id = nesneAc()
        yaz(
            "<< /Type /XObject /Subtype /Image /Width $w /Height $h " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${jpeg.size} >>\nstream\n"
        )
        out.write(jpeg)
        yaz("\nendstream\nendobj\n")
        adet++
        satir += Resim(id, w, h)
        if (satir.size == sutun) satiriYerlestir()
    }

    private fun satiriYerlestir() {
        if (satir.isEmpty()) return
        val yerler = duzen.satir(satir.map { it.w to it.h })
        if (yerler.first().sayfa != acikSayfa) {
            sayfayiYaz()
            acikSayfa = yerler.first().sayfa
        }
        satir.forEachIndexed { i, r -> sayfaResimleri += r to yerler[i] }
        satir.clear()
    }

    private fun sayfayiYaz() {
        if (sayfaResimleri.isEmpty()) return
        val icerik = buildString {
            for ((r, y) in sayfaResimleri) {
                // PDF'te y aşağıdan yukarı; Sayfalayici üstten veriyor.
                append(
                    String.format(
                        Locale.US, "q %.2f 0 0 %.2f %.2f %.2f cm /I%d Do Q\n",
                        y.w, y.h, y.x, Sayfalayici.A4_H - y.y - y.h, r.id
                    )
                )
            }
        }.toByteArray(Charsets.US_ASCII)
        val icerikId = nesneAc()
        yaz("<< /Length ${icerik.size} >>\nstream\n")
        out.write(icerik)
        yaz("\nendstream\nendobj\n")

        val sayfaId = nesneAc()
        val kaynaklar = sayfaResimleri.joinToString(" ") { (r, _) -> "/I${r.id} ${r.id} 0 R" }
        yaz(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${Sayfalayici.A4_W} ${Sayfalayici.A4_H}] " +
                "/Resources << /XObject << $kaynaklar >> >> /Contents $icerikId 0 R >>\nendobj\n"
        )
        sayfalar += sayfaId
        sayfaResimleri.clear()
    }

    override fun close() {
        if (kapandi) return
        kapandi = true
        satiriYerlestir()
        sayfayiYaz()
        if (sayfalar.isEmpty()) {
            // Görselsiz PDF de açılabilsin: tek boş sayfa.
            val sayfaId = nesneAc()
            yaz("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${Sayfalayici.A4_W} ${Sayfalayici.A4_H}] >>\nendobj\n")
            sayfalar += sayfaId
        }
        nesneAc(2)
        yaz("<< /Type /Pages /Kids [${sayfalar.joinToString(" ") { "$it 0 R" }}] /Count ${sayfalar.size} >>\nendobj\n")
        nesneAc(1)
        yaz("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        val xref = out.sayac
        yaz("xref\n0 ${sonId + 1}\n0000000000 65535 f \n")
        for (id in 1..sonId) {
            yaz(String.format(Locale.US, "%010d 00000 n \n", ofsetler.getValue(id)))
        }
        yaz("trailer\n<< /Size ${sonId + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.close()
    }

    private fun nesneAc(id: Int = ++sonId): Int {
        ofsetler[id] = out.sayac
        yaz("$id 0 obj\n")
        return id
    }

    private fun yaz(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

    /** Yazılan bayt sayısını tutan akış (xref ofsetleri için). */
    private class SayanAkis(o: OutputStream) : FilterOutputStream(o) {
        var sayac = 0L
            private set

        override fun write(b: Int) {
            out.write(b); sayac++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len); sayac += len
        }
    }

    companion object {
        /** Sayfa kenar boşluğu ve görseller arası boşluk, nokta. */
        const val KENAR = 24f
        const val ARALIK = 12f
    }
}
