package com.emre.bilbakalim.arsiv.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.emre.bilbakalim.arsiv.capture.OcrEngine
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Soruların ekran görüntülerini dışa aktarır: ZIP (fotoğraflar), PDF ya da
 * Word. Her soru [SoruGorseli] ile tek bir görsel olarak çiziliyor (soru,
 * şıklar, doğru şıkkın işareti ve cevabı); PDF ve Word'de bir soru hiçbir
 * zaman iki sayfaya bölünmüyor.
 *
 * Hız: yan dosyası olan (yakalamada şık metni yazılmış) ya da kararı
 * görüntüde yeşil olan soru ~0,1 sn; eski görüntüde şıklar bir kez OCR'la
 * okunuyor (~0,3 sn) ve sonuç yan dosyaya yazılıyor, ikinci dışa aktarım
 * hızlı. Sorular [PARALEL] tanesi birden çiziliyor, dosyaya sırayla
 * yazılıyor.
 */
object GorselDisaAktarim {

    enum class Bicim(val uzanti: String, val mime: String, val etiket: String) {
        PDF("pdf", "application/pdf", "PDF"),
        WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
        ZIP("zip", "application/zip", "Fotoğraflar (ZIP)")
    }

    data class Ilerleme(val yapilan: Int, val toplam: Int, val isaretli: Int, val gecenMs: Long) {
        /** Şimdiye kadarki hıza göre kalan süre, saniye; henüz tahmin yoksa null. */
        val kalanSn: Int?
            get() = if (yapilan < 3) null else ((gecenMs.toDouble() / yapilan) * (toplam - yapilan) / 1000).toInt()
    }

    class Sonuc(val dosya: File, val bicim: Bicim, val adet: Int, val isaretli: Int, val atlanan: Int)

    /**
     * Başlamadan önceki kaba süre tahmini, saniye (en iyi, en kötü): soru
     * başına paralel çizimle ~0,05 sn; eski görüntünün şıkları OCR'la
     * okunacaksa ~0,3 sn.
     */
    fun tahminiSure(adet: Int): Pair<Int, Int> =
        maxOf(1, (adet * 0.05).toInt()) to maxOf(2, (adet * 0.3).toInt())

    /** Ekran görüntüsü diskte duran kayıtlar; dışa aktarılabilecek olanlar. */
    fun gorselliler(rows: List<QuestionEntity>): List<QuestionEntity> =
        rows.filter { r -> r.screenshotPath?.let { File(it).isFile } == true }

    /**
     * [rows]'u (ekran görüntüsü olanları) [bicim]'de yazar. [sutun]: PDF ve
     * Word'de sayfada kaç sütun (1: büyük, 2: sayfa başına daha çok soru).
     */
    suspend fun yaz(
        context: Context,
        rows: List<QuestionEntity>,
        bicim: Bicim,
        sutun: Int,
        sikBolgesi: EkranBolgesi?,
        ilerleme: (Ilerleme) -> Unit
    ): Sonuc = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "export").apply { mkdirs() }
        // Önceki görsel dışa aktarımlar yer kaplamasın (paylaşılmış olanlar
        // alan uygulamaya çoktan kopyalandı).
        dir.listFiles { f -> f.name.startsWith(ON_EK) }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val dosya = File(dir, "$ON_EK$stamp.${bicim.uzanti}")

        val secili = gorselliler(rows)
        val taniyici = OcrEngine.yeniTaniyici()
        val ocr: suspend (Bitmap) -> List<com.emre.bilbakalim.arsiv.capture.TextItem> =
            { OcrEngine.recognizeWith(taniyici, it) }
        val baslangic = System.currentTimeMillis()
        var yapilan = 0
        var isaretli = 0
        var atlanan = 0
        try {
            yazici(bicim, dosya, sutun).use { cikis ->
                for (grup in secili.withIndex().chunked(PARALEL)) {
                    ensureActive()
                    val kartlar = coroutineScope {
                        grup.map { (i, r) ->
                            async(Dispatchers.Default) {
                                runCatching {
                                    SoruGorseli.ciz(r, baslik(i + 1, r), sikBolgesi, ocr)
                                }.getOrNull()
                            }
                        }.awaitAll()
                    }
                    grup.forEachIndexed { j, (i, r) ->
                        val k = kartlar[j]
                        if (k == null) atlanan++ else {
                            cikis.ekle(i + 1, r, k)
                            if (k.isaretli) isaretli++
                        }
                        yapilan++
                    }
                    ilerleme(Ilerleme(yapilan, secili.size, isaretli, System.currentTimeMillis() - baslangic))
                }
            }
        } catch (t: Throwable) {
            dosya.delete()
            throw t
        } finally {
            runCatching { taniyici.close() }
        }
        Sonuc(dosya, bicim, yapilan - atlanan, isaretli, atlanan)
    }

    fun paylas(context: Context, dosya: File, bicim: Bicim) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dosya)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = bicim.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, dosya.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Soruları paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun baslik(no: Int, r: QuestionEntity) = listOfNotNull(
        "$no. soru", r.category?.takeIf { it.isNotBlank() }, "${r.seenCount} kez çıktı", "#${r.id}"
    ).joinToString(" · ")

    /** Biçimden bağımsız çıkış: görseller sırayla geliyor. */
    private interface Cikis : Closeable {
        fun ekle(no: Int, r: QuestionEntity, k: SoruGorseli.Sonuc)
    }

    private fun yazici(bicim: Bicim, dosya: File, sutun: Int): Cikis = when (bicim) {
        Bicim.PDF -> object : Cikis {
            val pdf = PdfYazici(dosya.outputStream(), sutun)
            override fun ekle(no: Int, r: QuestionEntity, k: SoruGorseli.Sonuc) = pdf.resimEkle(k.jpeg, k.w, k.h)
            override fun close() = pdf.close()
        }
        Bicim.WORD -> object : Cikis {
            val doc = DocxYazici(dosya.outputStream(), sutun)
            override fun ekle(no: Int, r: QuestionEntity, k: SoruGorseli.Sonuc) = doc.resimEkle(k.jpeg, k.w, k.h)
            override fun close() = doc.close()
        }
        Bicim.ZIP -> ZipCikis(dosya)
    }

    /**
     * Fotoğraflar sıkıştırılmadan (JPEG zaten sıkışık) ve bir içindekiler
     * dosyası: numara, kategori, kaç kez çıktı, soru, doğru cevap.
     */
    private class ZipCikis(dosya: File) : Cikis {
        private val zip = ZipOutputStream(BufferedOutputStream(dosya.outputStream(), 1 shl 16))
        private val liste = StringBuilder("no\tkayit\tkategori\tkac_kez_cikti\tsoru\tdogru_cevap\n")

        override fun ekle(no: Int, r: QuestionEntity, k: SoruGorseli.Sonuc) {
            val ad = String.format(Locale.US, "%04d_%s.jpg", no, dosyaAdi(r.category ?: "kategorisiz"))
            val crc = CRC32().apply { update(k.jpeg) }
            zip.putNextEntry(ZipEntry(ad).apply {
                method = ZipEntry.STORED
                size = k.jpeg.size.toLong()
                compressedSize = k.jpeg.size.toLong()
                this.crc = crc.value
            })
            zip.write(k.jpeg)
            zip.closeEntry()
            liste.append(no).append('\t').append(r.id).append('\t')
                .append(r.category.orEmpty().tekSatir()).append('\t')
                .append(r.seenCount).append('\t')
                .append(r.questionText.tekSatir()).append('\t')
                .append(r.correctText.orEmpty().tekSatir()).append('\n')
        }

        override fun close() {
            zip.putNextEntry(ZipEntry("icindekiler.txt"))
            zip.write(liste.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.close()
        }

        private fun String.tekSatir() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    }

    /** Dosya adında sorun çıkarmayacak kısa bir kategori adı ("Matematik" → "matematik"). */
    internal fun dosyaAdi(s: String): String =
        TurkishText.fold(TurkishText.lower(s)).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24)
            .ifEmpty { "soru" }

    private const val ON_EK = "soru_gorselleri_"
    /** Aynı anda çizilen soru sayısı: bellek ~15 MB, hız ~2,5 kat. */
    private const val PARALEL = 3
}
