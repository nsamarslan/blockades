package com.emre.bilbakalim.arsiv.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.emre.bilbakalim.arsiv.capture.OptionBoxFinder
import com.emre.bilbakalim.arsiv.capture.SikImzasi
import com.emre.bilbakalim.arsiv.capture.SoruKartiDuzeni
import com.emre.bilbakalim.arsiv.capture.SoruKartiDuzeni.HapRengi
import com.emre.bilbakalim.arsiv.capture.SoruKartiDuzeni.SikYerlesimi
import com.emre.bilbakalim.arsiv.capture.TextItem
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Bir sorunun ekran görüntüsünden dışa aktarılacak kartı çizer: soru ve
 * şıklar kırpılıyor, doğru şık yeşil bir çerçeve ve sağdan gelen bir okla
 * işaretleniyor, altına doğru cevap yazılıyor.
 *
 * Doğru şıkkın görüntüdeki yeri [SoruKartiDuzeni.dogruKutu]'dan: oyun
 * kararı açmışsa (yanlış seçilen kırmızı, doğrusu yeşil) yeşil hap, yoksa
 * arşivdeki cevabın metni görüntüdeki şık metinleriyle eşleştiriliyor.
 * Şık metinleri yakalama anında yazılan yan dosyadan geliyor; yoksa (eski
 * görüntüler) bir kez OCR'la okunup yan dosyaya yazılıyor. Emin
 * olunamayan soruda ok çizilmiyor, yalnızca doğru cevap yazılıyor.
 */
object SoruGorseli {

    class Sonuc(val jpeg: ByteArray, val w: Int, val h: Int, val isaretli: Boolean)

    /** Ekran görüntüsünün yanındaki şık yerleşimi dosyası. */
    fun yerlesimDosyasi(goruntu: File) = File(goruntu.parentFile, goruntu.nameWithoutExtension + ".json")

    /**
     * @param baslik kartın üstüne yazılacak satır ("12. soru · Matematik").
     * @param ocr şık metni gerektiğinde okuyucu; null ise OCR yapılmaz.
     * @return görüntü yoksa ya da açılamıyorsa null.
     */
    suspend fun ciz(
        row: QuestionEntity,
        baslik: String,
        sikBolgesi: EkranBolgesi?,
        ocr: (suspend (Bitmap) -> List<TextItem>)?
    ): Sonuc? {
        val dosya = row.screenshotPath?.let { File(it) }?.takeIf { it.isFile } ?: return null
        val bmp = BitmapFactory.decodeFile(dosya.path) ?: return null
        return try {
            cizBitmap(bmp, dosya, row, baslik, sikBolgesi, ocr)
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun cizBitmap(
        bmp: Bitmap,
        dosya: File,
        row: QuestionEntity,
        baslik: String,
        sikBolgesi: EkranBolgesi?,
        ocr: (suspend (Bitmap) -> List<TextItem>)?
    ): Sonuc {
        val w = bmp.width
        val h = bmp.height
        val yanDosya = yerlesimDosyasi(dosya)
        val yerlesim = if (yanDosya.isFile) {
            runCatching { SikYerlesimi.oku(yanDosya.readText()) }.getOrNull()?.takeIf { it.gecerli(w, h) }
        } else null

        val bant = sikBolgesi?.let {
            OptionBoxFinder.Bant((it.sol * w).toInt(), (it.ust * h).toInt(), (it.sag * w).toInt(), (it.alt * h).toInt())
        }
        val olculen = (bant?.let { OptionBoxFinder.find(bmp, it) }.orEmpty())
            .ifEmpty { OptionBoxFinder.find(bmp) }
            .sortedBy { it.top }

        // Yan dosyadaki şıklar üstten alta diziliyor. Pikselden ölçülen haplar
        // aynı sayıdaysa onların kutusu (hapın tamamı) kullanılıyor.
        val yanSira = yerlesim?.let { y -> y.kutular.indices.sortedBy { y.kutular[it].top } }
        val kutular = when {
            yerlesim != null && olculen.size == yerlesim.kutular.size -> olculen
            yerlesim != null -> yanSira!!.map { yerlesim.kutular[it] }
            else -> olculen
        }
        var metinler: List<String>? = yanSira?.map { yerlesim!!.siklar[it] }

        var karar: SoruKartiDuzeni.Karar? = null
        if (kutular.size >= 2) {
            val renkler = kutular.map { zemin(bmp, it) }
            val imzalar = kutular.map { imza(bmp, it) }
            val tekYesil = renkler.count { it == HapRengi.YESIL } == 1
            if (metinler == null && !tekYesil && row.correctIndex != null && ocr != null) {
                metinler = ocrIleOku(bmp, kutular, ocr)
                // Bir sonraki dışa aktarım OCR'sız geçsin.
                if (metinler != null) {
                    runCatching { yanDosya.writeText(SikYerlesimi(metinler, kutular, "ocr").json()) }
                }
            }
            karar = SoruKartiDuzeni.dogruKutu(
                renkler, row.options, row.imzalar, row.correctIndex, metinler, imzalar
            )
        }

        val kirp = if (kutular.isEmpty()) SoruKartiDuzeni.Kirpma(0, 0, w, h)
        else SoruKartiDuzeni.kirpma(kutular, kartBul(bmp, kutular), w, h)

        val cevapMetni = row.correctText?.takeIf { it != TurkishText.UNREADABLE_OPTION }
            ?: karar?.let { k -> metinler?.getOrNull(k.sira)?.takeIf { it != TurkishText.UNREADABLE_OPTION } }
        val altYazi = when {
            karar != null && cevapMetni != null -> "Doğru cevap: «$cevapMetni»"
            karar != null -> "Doğru cevap: işaretli şık"
            cevapMetni != null -> "Doğru cevap: «$cevapMetni» (görüntüde yeri bulunamadı)"
            else -> "Doğru cevap arşivde yok"
        }
        return birlestir(bmp, kirp, karar?.let { kutular[it.sira] }, baslik, altYazi)
    }

    /** Hapın ortasından zemin rengi (uçlar yuvarlak, yazı koyu: ikisi de atlanıyor). */
    private fun zemin(bmp: Bitmap, k: OptionBoxFinder.Box): HapRengi? {
        val x0 = (k.left + k.width * 0.12f).toInt().coerceIn(0, bmp.width - 1)
        val x1 = (k.right - k.width * 0.12f).toInt().coerceIn(x0 + 1, bmp.width)
        val y0 = (k.top + k.height * 0.2f).toInt().coerceIn(0, bmp.height - 1)
        val y1 = (k.bottom - k.height * 0.2f).toInt().coerceIn(y0 + 1, bmp.height)
        val px = IntArray((x1 - x0) * (y1 - y0))
        if (runCatching { bmp.getPixels(px, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0) }.isFailure) return null
        return SoruKartiDuzeni.zeminRengi(px)
    }

    /** Yakalamadaki ile aynı kırpmayla şık yazısının piksel imzası. */
    private fun imza(bmp: Bitmap, k: OptionBoxFinder.Box): String? {
        val payX = k.height / 2
        val payY = (k.height * 0.12f).toInt()
        val left = (k.left + payX).coerceIn(0, bmp.width - 1)
        val right = (k.right - payX).coerceIn(left + 1, bmp.width)
        val top = (k.top + payY).coerceIn(0, bmp.height - 1)
        val bottom = (k.bottom - payY).coerceIn(top + 1, bmp.height)
        val cw = right - left
        val ch = bottom - top
        if (cw < 4 || ch < 4) return null
        val px = IntArray(cw * ch)
        if (runCatching { bmp.getPixels(px, 0, cw, left, top, cw, ch) }.isFailure) return null
        return SikImzasi.hesapla(px, cw, ch)
    }

    /** Şık şeridini iki kat büyütüp okur; her kutunun metni (okunamayan: «(okunamadı)»). */
    private suspend fun ocrIleOku(
        bmp: Bitmap,
        kutular: List<OptionBoxFinder.Box>,
        ocr: suspend (Bitmap) -> List<TextItem>
    ): List<String>? {
        val x0 = (kutular.minOf { it.left } - 4).coerceAtLeast(0)
        val x1 = (kutular.maxOf { it.right } + 4).coerceAtMost(bmp.width)
        val y0 = (kutular.first().top - 4).coerceAtLeast(0)
        val y1 = (kutular.last().bottom + 4).coerceAtMost(bmp.height)
        if (x1 - x0 < 8 || y1 - y0 < 8) return null
        val serit = Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
        val buyuk = Bitmap.createScaledBitmap(serit, serit.width * OCR_OLCEK, serit.height * OCR_OLCEK, true)
        if (buyuk !== serit) serit.recycle()
        val ogeler = try { ocr(buyuk) } finally { buyuk.recycle() }
        if (ogeler.isEmpty()) return null

        val parcalar = List(kutular.size) { ArrayList<Pair<String, IntRange>>() }
        for (blok in ogeler) {
            // Alt alta kısa şıklar tek bloğa toplanabiliyor: satırlarına bakılıyor.
            for (satir in blok.lines.ifEmpty { listOf(blok) }) {
                val b = satir.bounds
                val cx = x0 + b.centerX() / OCR_OLCEK
                val cy = y0 + b.centerY() / OCR_OLCEK
                val i = kutular.indexOfFirst { cy in it.top..it.bottom && cx in it.left..it.right }
                if (i >= 0) parcalar[i] += satir.text to (x0 + b.left / OCR_OLCEK)..(x0 + b.right / OCR_OLCEK)
            }
        }
        return parcalar.map { p -> SoruKartiDuzeni.sikMetni(p.sortedBy { it.second.first }) }
    }

    /** Hapların biraz genişletilmiş aralığında, ilk hapın üstündeki soru kartı. */
    private fun kartBul(bmp: Bitmap, kutular: List<OptionBoxFinder.Box>): SoruKartiDuzeni.Kart? {
        val pw = kutular.maxOf { it.width }
        val x0 = (kutular.minOf { it.left } - pw / 4).coerceAtLeast(0)
        val x1 = (kutular.maxOf { it.right } + pw / 4).coerceAtMost(bmp.width)
        val genislik = x1 - x0
        if (genislik < 16) return null
        val ilk = kutular.minOf { it.top }
        val adim = (genislik / 120).coerceAtLeast(1)
        val buf = IntArray(bmp.width)
        val satirlar = ArrayList<OptionBoxFinder.Row>(ilk / OptionBoxFinder.ROW_STEP + 1)
        var y = 0
        while (y < ilk) {
            if (runCatching { bmp.getPixels(buf, 0, bmp.width, 0, y, bmp.width, 1) }.isFailure) return null
            val r = OptionBoxFinder.measure(y, buf, x1, adim, x0)
            // Ölçü [x0, x1) aralığına göre; kart kuralı da o genişliği bekliyor.
            satirlar += r
            y += OptionBoxFinder.ROW_STEP
        }
        return SoruKartiDuzeni.kartBul(satirlar, ilk, genislik, bmp.height)
    }

    /** Başlık + kırpılmış görüntü (işaretli) + alt yazı; JPEG. */
    private fun birlestir(
        bmp: Bitmap,
        kirp: SoruKartiDuzeni.Kirpma,
        dogru: OptionBoxFinder.Box?,
        baslik: String,
        altYazi: String
    ): Sonuc {
        val oluk = if (dogru != null) (kirp.w * 0.13f).toInt().coerceAtLeast(80) else 0
        val genislik = kirp.w + oluk
        val birim = genislik / 30f
        val pay = birim * 0.7f

        val baslikKalem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = birim * 0.95f
            typeface = Typeface.DEFAULT_BOLD
        }
        val altKalem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dogru != null) KOYU_YESIL else Color.rgb(60, 60, 60)
            textSize = birim * 1.1f
            typeface = Typeface.DEFAULT_BOLD
        }
        val baslikH = (baslikKalem.textSize * 1.7f).toInt()
        val altSatirlar = sar(altYazi, altKalem, genislik - 2 * pay, 3)
        val altH = (altKalem.textSize * (1.35f * altSatirlar.size + 0.8f)).toInt()
        val yukseklik = baslikH + kirp.h + altH

        val out = Bitmap.createBitmap(genislik, yukseklik, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawText(kisalt(baslik, baslikKalem, genislik - 2 * pay), pay, baslikH * 0.68f, baslikKalem)

        val parca = Bitmap.createBitmap(bmp, kirp.sol, kirp.ust, kirp.w, kirp.h)
        c.drawBitmap(parca, 0f, baslikH.toFloat(), null)
        parca.recycle()

        if (dogru != null) isaretle(c, dogru, kirp, baslikH, genislik, birim)

        var yz = baslikH + kirp.h + altKalem.textSize * 1.3f
        for (s in altSatirlar) {
            c.drawText(s, pay, yz, altKalem)
            yz += altKalem.textSize * 1.35f
        }

        val bayt = ByteArrayOutputStream(64 * 1024)
        out.compress(Bitmap.CompressFormat.JPEG, JPEG_KALITE, bayt)
        out.recycle()
        return Sonuc(bayt.toByteArray(), genislik, yukseklik, dogru != null)
    }

    /** Doğru hapın çevresine çerçeve, sağdaki şeritten hapa bir ok ve "DOĞRU". */
    private fun isaretle(
        c: Canvas, k: OptionBoxFinder.Box, kirp: SoruKartiDuzeni.Kirpma,
        ustBosluk: Int, genislik: Int, birim: Float
    ) {
        val kalin = (birim * 0.28f).coerceAtLeast(4f)
        val r = RectF(
            (k.left - kirp.sol).toFloat(), (k.top - kirp.ust + ustBosluk).toFloat(),
            (k.right - kirp.sol).toFloat(), (k.bottom - kirp.ust + ustBosluk).toFloat()
        ).apply { inset(-kalin, -kalin) }
        val yaricap = r.height() / 2
        val kalem = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        // Beyaz hale: çerçeve yeşil hapın üstünde de, mor zeminde de seçilsin.
        kalem.color = Color.WHITE; kalem.strokeWidth = kalin * 1.9f
        c.drawRoundRect(r, yaricap, yaricap, kalem)
        kalem.color = KOYU_YESIL; kalem.strokeWidth = kalin
        c.drawRoundRect(r, yaricap, yaricap, kalem)

        // Ok: ucu çerçevenin hemen sağında, gövdesi sağdaki şeride uzanıyor.
        val cy = r.centerY()
        val uc = r.right + kalin
        val kuyruk = genislik - birim * 0.6f
        val basUzun = birim * 1.6f
        val basYari = birim * 1.0f
        val govdeYari = birim * 0.32f
        if (kuyruk - uc < basUzun + birim) return
        val ok = Path().apply {
            moveTo(uc, cy)
            lineTo(uc + basUzun, cy - basYari)
            lineTo(uc + basUzun, cy - govdeYari)
            lineTo(kuyruk, cy - govdeYari)
            lineTo(kuyruk, cy + govdeYari)
            lineTo(uc + basUzun, cy + govdeYari)
            lineTo(uc + basUzun, cy + basYari)
            close()
        }
        c.drawPath(ok, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACIK_YESIL; style = Paint.Style.FILL })
        c.drawPath(ok, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = kalin * 0.6f
        })
        val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = KOYU_YESIL
            textSize = birim * 0.8f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val etiketX = (kirp.w + genislik) / 2f
        val etiketY = cy - basYari - birim * 0.35f
        // Etiketin arkası beyaz: mor zeminde de okunsun.
        val eW = yazi.measureText("DOĞRU") / 2 + birim * 0.2f
        c.drawRoundRect(
            RectF(etiketX - eW, etiketY - yazi.textSize, etiketX + eW, etiketY + yazi.textSize * 0.3f),
            birim * 0.2f, birim * 0.2f, Paint().apply { color = Color.WHITE }
        )
        c.drawText("DOĞRU", etiketX, etiketY, yazi)
    }

    /** Metni [maxW] genişliğe sığan en fazla [enCok] satıra böler; taşan son satır "…" ile biter. */
    private fun sar(metin: String, kalem: Paint, maxW: Float, enCok: Int): List<String> {
        val satirlar = ArrayList<String>()
        var satir = ""
        for (kelime in metin.split(' ')) {
            val aday = if (satir.isEmpty()) kelime else "$satir $kelime"
            if (kalem.measureText(aday) <= maxW || satir.isEmpty()) satir = aday
            else {
                satirlar += satir
                satir = kelime
            }
        }
        if (satir.isNotEmpty()) satirlar += satir
        if (satirlar.size <= enCok) return satirlar.map { kisalt(it, kalem, maxW) }
        return satirlar.take(enCok - 1).map { kisalt(it, kalem, maxW) } +
            kisalt(satirlar.drop(enCok - 1).joinToString(" "), kalem, maxW)
    }

    private fun kisalt(s: String, kalem: Paint, maxW: Float): String {
        if (kalem.measureText(s) <= maxW) return s
        val n = kalem.breakText(s, true, maxW - kalem.measureText("…"), null)
        return s.take(n.coerceAtLeast(1)) + "…"
    }

    private const val OCR_OLCEK = 2
    private const val JPEG_KALITE = 82
    private val KOYU_YESIL = Color.rgb(12, 122, 40)
    private val ACIK_YESIL = Color.rgb(46, 204, 64)
}
