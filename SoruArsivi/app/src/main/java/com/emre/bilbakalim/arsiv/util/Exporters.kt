package com.emre.bilbakalim.arsiv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arşivi telefondan çıkarmanın üç yolu:
 *  - CSV  : Excel / Google E-Tablolar
 *  - JSON : başka bir programa aktarım
 *  - TSV  : Anki'ye kart olarak içe aktarım
 */
object Exporters {

    enum class Format(val uzanti: String, val mime: String, val etiket: String) {
        CSV("csv", "text/csv", "CSV (Excel)"),
        JSON("json", "application/json", "JSON"),
        ANKI("txt", "text/tab-separated-values", "Anki (TSV)")
    }

    fun write(context: Context, rows: List<QuestionEntity>, format: Format): File {
        val dir = File(context.filesDir, "export").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(dir, "soru_arsivi_$stamp.${format.uzanti}")

        val text = when (format) {
            Format.CSV -> toCsv(rows)
            Format.JSON -> toJson(rows)
            Format.ANKI -> toAnki(rows)
        }
        // BOM: Excel'in Türkçe karakterleri doğru göstermesi için.
        val prefix = if (format == Format.CSV) "\uFEFF" else ""
        file.writeText(prefix + text, Charsets.UTF_8)
        return file
    }

    fun share(context: Context, file: File, format: Format) {
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Arşivi paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun esc(s: String?): String {
        val v = s ?: ""
        return "\"" + v.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
    }

    private fun toCsv(rows: List<QuestionEntity>): String = buildString {
        append(
            "soru;A;B;C;D;dogru_sik;dogru_metin;cevap_kaynagi;kategori;" +
                "kac_kez_cikti;cevapladigin;dogru_bildigin;basari_yuzde;kaynak;guven;tarih\n"
        )
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (r in rows) {
            append(esc(r.questionText)).append(';')
            append(esc(r.optionA)).append(';')
            append(esc(r.optionB)).append(';')
            append(esc(r.optionC)).append(';')
            append(esc(r.optionD)).append(';')
            append(esc(r.correctIndex?.let { ('A' + it).toString() })).append(';')
            append(esc(r.correctText)).append(';')
            append(esc(r.answerSource)).append(';')
            append(esc(r.category)).append(';')
            append(r.seenCount).append(';')
            append(r.answeredCount).append(';')
            append(r.correctCount).append(';')
            append(r.successRate?.toString() ?: "").append(';')
            append(esc(r.source)).append(';')
            append(String.format(Locale.US, "%.2f", r.confidence)).append(';')
            append(esc(df.format(Date(r.capturedAt)))).append('\n')
        }
    }

    private fun toJson(rows: List<QuestionEntity>): String {
        val arr = JSONArray()
        for (r in rows) {
            arr.put(
                JSONObject().apply {
                    put("soru", r.questionText)
                    put("siklar", JSONArray(r.options))
                    put("dogruIndeks", r.correctIndex ?: JSONObject.NULL)
                    put("dogruMetin", r.correctText ?: JSONObject.NULL)
                    // Cevabın nereden öğrenildiği ("renk (kesin)", "renk",
                    // "süre doldu", "dokunuş", "elle", "içe aktarım").
                    // "kaynak" alanıyla karıştırılmamalı: o, sorunun ekrandan
                    // hangi yolla okunduğunu söylüyor. Zayıf kanıtla yazılmış
                    // cevapları ancak bu alanla ayıklayabilirsin.
                    put("cevapKaynagi", r.answerSource ?: JSONObject.NULL)
                    put("kategori", r.category ?: JSONObject.NULL)
                    put("kaynak", r.source)
                    put("guven", r.confidence)
                    put("kacKezCikti", r.seenCount)
                    put("cevapladigin", r.answeredCount)
                    put("dogruBildigin", r.correctCount)
                    put("basariYuzde", r.successRate ?: JSONObject.NULL)
                    put("tarih", r.capturedAt)
                    // Yedeğin geri yüklenebilmesi için: elle düzeltilmiş
                    // kayıtlar içe aktarmada korunuyor.
                    put("elleDuzenlendi", r.edited)
                    put("not", r.note ?: JSONObject.NULL)
                }
            )
        }
        return JSONObject().apply {
            put("olusturma", System.currentTimeMillis())
            put("adet", rows.size)
            put("sorular", arr)
        }.toString(2)
    }

    /** Ön yüz: soru + şıklar. Arka yüz: doğru cevap. */
    private fun toAnki(rows: List<QuestionEntity>): String = buildString {
        for (r in rows) {
            val correct = r.correctText ?: continue
            val front = buildString {
                append(r.questionText)
                r.options.forEachIndexed { i, o -> append("<br>").append('A' + i).append(") ").append(o) }
            }.replace("\t", " ")
            append(front).append('\t').append(correct.replace("\t", " ")).append('\n')
        }
    }
}
