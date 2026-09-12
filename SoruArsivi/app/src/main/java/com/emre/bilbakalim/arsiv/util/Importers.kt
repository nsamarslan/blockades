package com.emre.bilbakalim.arsiv.util

import com.emre.bilbakalim.arsiv.data.QuestionEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON yedeğini geri okur.
 *
 * Telefonu değiştirdiğinde ya da uygulamayı silip yeniden kurmak zorunda
 * kaldığında arşivin kaybolmasın diye var. Girdi, uygulamanın kendi JSON
 * çıktısıdır ([Exporters]).
 *
 * Buradaki her şey saf: veritabanına dokunmaz, dosya açmaz. Birleştirme
 * kararlarının cihazsız test edilebilmesi için böyle.
 */
object Importers {

    /** Yedekten okunan tek bir soru. */
    data class Row(
        val question: String,
        val options: List<String>,
        /**
         * Doğru cevabın **metni**.
         *
         * Sırası bilerek taşınmıyor: şıklar her turda karıştığı için yedekteki
         * sıra ile buradaki kaydın sırası birbirini tutmayabilir. Metin
         * taşındığında hangi listeye yazarsak yazalım doğru şıkka oturuyor.
         */
        val correctText: String?,
        val category: String?,
        val source: String,
        val confidence: Float,
        val seenCount: Int,
        val answeredCount: Int,
        val correctCount: Int,
        val edited: Boolean,
        val note: String?,
        val capturedAt: Long
    )

    /**
     * Metni ayrıştırır. Hem uygulamanın kendi çıktısını
     * (`{"sorular": [...]}`) hem de düz bir dizi (`[...]`) kabul eder.
     *
     * Okunamayan satırlar sessizce atlanır — yarım bir yedek yüzünden
     * tamamının reddedilmesi kimsenin işine yaramaz. Dosyanın tamamı
     * anlaşılamıyorsa [IllegalArgumentException] atar.
     */
    fun parse(text: String): List<Row> {
        val trimmed = text.trim().removePrefix("﻿").trim()
        val arr: JSONArray = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }
                .getOrElse { throw IllegalArgumentException("JSON okunamadı") }
            trimmed.startsWith("{") -> runCatching { JSONObject(trimmed) }
                .getOrElse { throw IllegalArgumentException("JSON okunamadı") }
                .optJSONArray("sorular")
                ?: throw IllegalArgumentException("Dosyada \"sorular\" listesi yok")
            else -> throw IllegalArgumentException("Bu bir JSON dosyası değil")
        }

        val out = ArrayList<Row>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(row(o) ?: continue)
        }
        return out
    }

    private fun row(o: JSONObject): Row? {
        val question = TurkishText.cleanOcr(o.str("soru") ?: return null)
        if (question.length < 8) return null

        val options = o.optJSONArray("siklar")
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) } }
            ?.map { TurkishText.cleanOcr(TurkishText.stripOptionPrefix(it)) }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (options.size < 2) return null

        // Eski yedeklerde yalnızca sıra olabilir; o zaman sıradan metne çeviriyoruz.
        val correctText = o.str("dogruMetin")
            ?: o.optIntOrNull("dogruIndeks")?.let { options.getOrNull(it) }

        val answered = o.optInt("cevapladigin", 0).coerceAtLeast(0)
        return Row(
            question = question,
            options = options.take(4),
            correctText = correctText,
            category = o.str("kategori"),
            source = o.str("kaynak") ?: "MANUAL",
            confidence = o.optDouble("guven", 1.0).toFloat().coerceIn(0f, 1f),
            seenCount = o.optInt("kacKezCikti", 1).coerceAtLeast(1),
            answeredCount = answered,
            correctCount = o.optInt("dogruBildigin", 0).coerceIn(0, answered),
            edited = o.optBoolean("elleDuzenlendi", false),
            note = o.str("not"),
            capturedAt = o.optLong("tarih", System.currentTimeMillis())
        )
    }

    /** Arşivde karşılığı olmayan satır — yeni kayıt olarak eklenir. */
    fun toEntity(row: Row): QuestionEntity {
        val opts = row.options.take(4)
        val correct = TurkishText.matchIndex(opts, row.correctText)
        return QuestionEntity(
            questionText = row.question,
            optionA = opts.getOrNull(0),
            optionB = opts.getOrNull(1),
            optionC = opts.getOrNull(2),
            optionD = opts.getOrNull(3),
            correctIndex = correct,
            answerSource = if (correct != null) ANSWER_SOURCE else null,
            category = row.category?.takeIf { it.isNotBlank() },
            source = row.source,
            confidence = row.confidence,
            fingerprint = TurkishText.fingerprint(row.question, opts),
            capturedAt = row.capturedAt,
            seenCount = row.seenCount,
            answeredCount = row.answeredCount,
            correctCount = row.correctCount,
            edited = row.edited,
            note = row.note
        )
    }

    /**
     * Arşivde zaten bulunan bir kayıtla yedekten geleni birleştirir.
     *
     * Kural: hiçbir bilgi kaybolmaz, hiçbir bilgi ezilmez. Cihazdaki kayıt
     * neyi biliyorsa o kalır; yalnızca eksikleri yedekten tamamlanır.
     * Sayaçlarda toplama değil **büyük olan** alınıyor: aynı dosyayı iki kez
     * içe aktarmak sayıları şişirmesin diye. Böylece içe aktarma yinelenebilir
     * bir işlem oluyor.
     */
    fun merge(existing: QuestionEntity, incoming: Row): QuestionEntity {
        // Eksik şıkları tamamla — ama var olanları yerinden oynatma, yoksa
        // kayıtlı doğru cevabın sırası kayar.
        val options = existing.options.toMutableList()
        for (o in incoming.options) {
            if (options.size >= 4) break
            if (TurkishText.matchIndex(options, o) == null) options.add(o)
        }

        val correct = existing.correctIndex
            ?: TurkishText.matchIndex(options, incoming.correctText)
        // Kaynak yalnızca cevabın kendisi yedekten geldiyse değişir; kayıtta
        // zaten bir cevap varsa nereden geldiği de olduğu gibi kalmalı.
        val answerFromBackup = existing.correctIndex == null && correct != null
        val answered = maxOf(existing.answeredCount, incoming.answeredCount)

        return existing.copy(
            optionA = options.getOrNull(0),
            optionB = options.getOrNull(1),
            optionC = options.getOrNull(2),
            optionD = options.getOrNull(3),
            correctIndex = correct,
            answerSource = if (answerFromBackup) ANSWER_SOURCE else existing.answerSource,
            category = existing.category?.takeIf { it.isNotBlank() }
                ?: incoming.category?.takeIf { it.isNotBlank() },
            confidence = maxOf(existing.confidence, incoming.confidence),
            // İlk ne zaman görüldüyse o kalsın.
            capturedAt = minOf(existing.capturedAt, incoming.capturedAt),
            seenCount = maxOf(existing.seenCount, incoming.seenCount),
            answeredCount = answered,
            correctCount = maxOf(existing.correctCount, incoming.correctCount)
                .coerceAtMost(answered),
            edited = existing.edited || incoming.edited,
            note = existing.note?.takeIf { it.isNotBlank() } ?: incoming.note,
            fingerprint = TurkishText.fingerprint(existing.questionText, options)
        )
    }

    /** Doğru cevabı yedekten aldığımızı gösterir. */
    const val ANSWER_SOURCE = "içe aktarım"

    // --- küçük JSON yardımcıları -------------------------------------------

    /** Boş ve JSON null değerlerini gerçekten null olarak döndürür. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, -1).takeIf { it >= 0 }
}
