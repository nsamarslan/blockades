package com.emre.bilbakalim.arsiv.util

import com.emre.bilbakalim.arsiv.data.QuestionEntity

/**
 * Dışa aktarılan soruların sırası. PDF / Word sayfaları, ZIP'teki dosya
 * numaraları ve metin biçimlerindeki satırlar bu sırayla.
 */
enum class DisaAktarimSirasi(val etiket: String) {
    /** "Kaç kez çıktı" sayacına göre: en çok karşılaşılan soru ilk sayfada. */
    EN_SIK("En sık çıkan önce"),
    KAYIT("Kaydedildiği sırayla");

    fun sirala(rows: List<QuestionEntity>): List<QuestionEntity> = when (this) {
        // Eşit sayıda çıkanlarda önce kaydedilen önde: sıra her dışa aktarımda aynı.
        EN_SIK -> rows.sortedWith(
            compareByDescending<QuestionEntity> { it.seenCount }.thenBy { it.capturedAt }.thenBy { it.id }
        )
        KAYIT -> rows.sortedWith(compareBy<QuestionEntity> { it.capturedAt }.thenBy { it.id })
    }
}
