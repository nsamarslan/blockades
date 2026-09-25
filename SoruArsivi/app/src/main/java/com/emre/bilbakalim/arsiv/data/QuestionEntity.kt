package com.emre.bilbakalim.arsiv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Yakalamanın hangi yoldan geldiğini gösterir. */
enum class CaptureSource { ACCESSIBILITY, OCR, HYBRID, MANUAL }

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["category"]),
        Index(value = ["capturedAt"])
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Soru cümlesi. */
    val questionText: String,

    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,

    /** 0=A, 1=B, 2=C, 3=D. Henüz tespit edilmediyse null. */
    val correctIndex: Int? = null,

    /** Doğru cevabın nasıl bulunduğu: "renk", "elle", null. */
    val answerSource: String? = null,

    val category: String? = null,

    /** ACCESSIBILITY / OCR / HYBRID / MANUAL */
    val source: String = CaptureSource.HYBRID.name,

    /** 0.0 - 1.0 arası ayrıştırma güveni. Düşükse arayüzde uyarı gösterilir. */
    val confidence: Float = 1f,

    val fingerprint: String,

    /** Uygulamanın kendi klasöründeki ekran görüntüsü dosya yolu. */
    val screenshotPath: String? = null,

    val capturedAt: Long = System.currentTimeMillis(),

    /** Aynı soruyla kaç kez karşılaşıldı. */
    val seenCount: Int = 1,

    /** Cevabını verdiğin kaç kez gözlendi (süre bittiyse sayılmaz). */
    val answeredCount: Int = 0,

    /** Bu denemelerin kaçında doğru bildin. */
    val correctCount: Int = 0,

    /** Kullanıcı elle düzelttiyse true; otomatik güncellemeler bunu ezmez. */
    val edited: Boolean = false,

    val note: String? = null,

    /**
     * Şıkların piksel imzaları, şıklarla aynı sırada, ";" ile ayrılmış (bkz.
     * `capture.SikImzasi`). Metni aynı okunan sembol şıkları (∨ ∧ > hepsi
     * «V») ancak bununla ayırt ediliyor. Boş parça: o şıkkın imzası yok.
     * Şıkların sırası değişen her yazımda ya yenisiyle değiştirilmeli ya da
     * silinmeli; yoksa imza başka bir şıkkı gösterir.
     */
    val optionSigs: String? = null
) {
    val options: List<String>
        get() = listOfNotNull(optionA, optionB, optionC, optionD)

    /** [optionSigs] şık sırasıyla; eksik ya da bozuksa boş liste. */
    val imzalar: List<String?>
        get() {
            val parcalar = optionSigs?.split(';') ?: return emptyList()
            if (parcalar.size != options.size) return emptyList()
            return parcalar.map { it.ifEmpty { null } }
        }

    val correctText: String?
        get() = correctIndex?.let { options.getOrNull(it) }

    val isComplete: Boolean
        get() = questionText.isNotBlank() && options.size >= 2

    /** Doğru bilme oranı — hiç cevaplanmadıysa null. */
    val successRate: Int?
        get() = if (answeredCount > 0) correctCount * 100 / answeredCount else null

    /** "4 kez çıktı · 3 denemede %67" gibi tek satırlık özet. */
    val statsLine: String
        get() = buildString {
            append(seenCount).append(" kez çıktı")
            if (answeredCount > 0) {
                append(" · ").append(answeredCount).append(" denemede %").append(successRate)
            }
        }
}
