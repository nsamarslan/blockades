package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Basit ayar deposu. SharedPreferences üstüne Compose'un dinleyebileceği
 * bir StateFlow sarmalıyor.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    data class Settings(
        /** Hangi uygulamalar izlensin (paket adları). Boşsa hiçbir şey yakalanmaz. */
        val targetPackages: Set<String> = emptySet(),
        /** Yakalama tamamen duraklatıldı mı. */
        val paused: Boolean = false,
        /** Kayıtlara yazılacak kategori etiketi (ör. "Felsefe"). */
        val activeCategory: String = "",
        /** Kategori adını ekrandan otomatik tanımaya çalış. */
        val autoDetectCategory: Boolean = true,
        /** Erişilebilirlik metni yetersizse ekran görüntüsü + OCR'a düş. */
        val ocrFallback: Boolean = true,
        /** Erişilebilirlik başarılı olsa bile OCR ile karşılaştır (daha yavaş). */
        val ocrAlways: Boolean = false,
        /** Cevap verildikten sonra yeşile dönen şıkkı doğru cevap olarak işaretle. */
        val detectAnswer: Boolean = true,
        /** Her soru için ekran görüntüsünü sakla (yer kaplar ama denetlemesi kolay). */
        val saveScreenshots: Boolean = true,
        /** Soru metninin aranacağı ekran bölgesi (ekran yüksekliğine oran). */
        val questionTop: Float = 0.08f,
        val questionBottom: Float = 0.55f,
        /** Şıkların aranacağı bölge. */
        val optionsTop: Float = 0.45f,
        val optionsBottom: Float = 0.97f,
        /** Bu değerin altındaki ayrıştırmalar kaydedilmez. */
        val minConfidence: Float = 0.45f,
        /** Sadece gerçekten soru cümlesine benzeyen metinleri kaydet. */
        val requireQuestionShape: Boolean = true,
        /** Dört şıkkın tamamı görünmeden kaydetme (şıklar teker teker beliriyor). */
        val requireFourOptions: Boolean = true,
        /**
         * Otomatik mod: uygulama şıklardan birini kendi seçip dokunur ve tur
         * bitince yeni tur başlatır. Kapalıyken (manuel mod) ekrana hiç
         * dokunulmaz, sadece okunur.
         */
        val autoPlay: Boolean = false,
        /** Soru göründükten sonra dokunmadan önce beklenen süre (ms). */
        val autoAnswerDelayMs: Long = 900L,
        /** Tur bitince "Tekrar Oyna" benzeri düğmeye bas. */
        val autoRestart: Boolean = true,
        /** Cevabı arşivde olan sorularda rastgele değil doğru şıkka bas. */
        val autoUseKnownAnswer: Boolean = false,
        /** Teşhis ekranı için son ham yakalama dökümü. */
        val lastDebugDump: String = "",
        /** Bilgilendirme ekranı gösterildi mi. */
        val onboarded: Boolean = false
    )

    private fun read() = Settings(
        targetPackages = sp.getStringSet(K_TARGETS, emptySet()) ?: emptySet(),
        paused = sp.getBoolean(K_PAUSED, false),
        activeCategory = sp.getString(K_CATEGORY, "") ?: "",
        autoDetectCategory = sp.getBoolean(K_AUTO_CAT, true),
        ocrFallback = sp.getBoolean(K_OCR_FALLBACK, true),
        ocrAlways = sp.getBoolean(K_OCR_ALWAYS, false),
        detectAnswer = sp.getBoolean(K_DETECT_ANSWER, true),
        saveScreenshots = sp.getBoolean(K_SHOTS, true),
        questionTop = sp.getFloat(K_Q_TOP, 0.08f),
        questionBottom = sp.getFloat(K_Q_BOTTOM, 0.55f),
        optionsTop = sp.getFloat(K_O_TOP, 0.45f),
        optionsBottom = sp.getFloat(K_O_BOTTOM, 0.97f),
        minConfidence = sp.getFloat(K_MIN_CONF, 0.45f),
        requireQuestionShape = sp.getBoolean(K_REQ_Q, true),
        requireFourOptions = sp.getBoolean(K_REQ_4, true),
        autoPlay = sp.getBoolean(K_AUTO_PLAY, false),
        autoAnswerDelayMs = sp.getLong(K_AUTO_DELAY, 900L),
        autoRestart = sp.getBoolean(K_AUTO_RESTART, true),
        autoUseKnownAnswer = sp.getBoolean(K_AUTO_KNOWN, false),
        lastDebugDump = sp.getString(K_DEBUG, "") ?: "",
        onboarded = sp.getBoolean(K_ONBOARDED, false)
    )

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _state.value = read()
    }

    fun setTargets(pkgs: Set<String>) = commit { putStringSet(K_TARGETS, pkgs) }
    fun setPaused(v: Boolean) = commit { putBoolean(K_PAUSED, v) }
    fun setCategory(v: String) = commit { putString(K_CATEGORY, v) }
    fun setAutoDetectCategory(v: Boolean) = commit { putBoolean(K_AUTO_CAT, v) }
    fun setOcrFallback(v: Boolean) = commit { putBoolean(K_OCR_FALLBACK, v) }
    fun setOcrAlways(v: Boolean) = commit { putBoolean(K_OCR_ALWAYS, v) }
    fun setDetectAnswer(v: Boolean) = commit { putBoolean(K_DETECT_ANSWER, v) }
    fun setSaveScreenshots(v: Boolean) = commit { putBoolean(K_SHOTS, v) }
    fun setMinConfidence(v: Float) = commit { putFloat(K_MIN_CONF, v) }
    fun setRequireQuestionShape(v: Boolean) = commit { putBoolean(K_REQ_Q, v) }
    fun setRequireFourOptions(v: Boolean) = commit { putBoolean(K_REQ_4, v) }
    fun setAutoPlay(v: Boolean) = commit { putBoolean(K_AUTO_PLAY, v) }
    fun setAutoAnswerDelay(ms: Long) = commit { putLong(K_AUTO_DELAY, ms.coerceIn(200L, 5000L)) }
    fun setAutoRestart(v: Boolean) = commit { putBoolean(K_AUTO_RESTART, v) }
    fun setAutoUseKnownAnswer(v: Boolean) = commit { putBoolean(K_AUTO_KNOWN, v) }
    fun setOnboarded(v: Boolean) = commit { putBoolean(K_ONBOARDED, v) }
    fun setDebugDump(v: String) = commit { putString(K_DEBUG, v) }

    fun setRegions(qTop: Float, qBottom: Float, oTop: Float, oBottom: Float) = commit {
        putFloat(K_Q_TOP, qTop); putFloat(K_Q_BOTTOM, qBottom)
        putFloat(K_O_TOP, oTop); putFloat(K_O_BOTTOM, oBottom)
    }

    fun resetRegions() = setRegions(0.08f, 0.55f, 0.45f, 0.97f)

    companion object {
        private const val K_TARGETS = "hedef_paketler"
        private const val K_PAUSED = "duraklatildi"
        private const val K_CATEGORY = "kategori"
        private const val K_AUTO_CAT = "kategori_otomatik"
        private const val K_OCR_FALLBACK = "ocr_yedek"
        private const val K_OCR_ALWAYS = "ocr_her_zaman"
        private const val K_DETECT_ANSWER = "cevap_tespiti"
        private const val K_SHOTS = "ekran_goruntusu"
        private const val K_Q_TOP = "soru_ust"
        private const val K_Q_BOTTOM = "soru_alt"
        private const val K_O_TOP = "sik_ust"
        private const val K_O_BOTTOM = "sik_alt"
        private const val K_MIN_CONF = "min_guven"
        private const val K_REQ_Q = "soru_sekli_zorunlu"
        private const val K_REQ_4 = "dort_sik_zorunlu"
        private const val K_AUTO_PLAY = "otomatik_mod"
        private const val K_AUTO_DELAY = "otomatik_gecikme"
        private const val K_AUTO_RESTART = "otomatik_yeniden_basla"
        private const val K_AUTO_KNOWN = "otomatik_bilinen_cevap"
        private const val K_DEBUG = "teshis_dokumu"
        private const val K_ONBOARDED = "tanitim_goruldu"

        /** Bil Bakalım ve benzeri uygulamalarda geçen kategori adları. */
        val BILINEN_KATEGORILER = listOf(
            "Felsefe", "Genel Kültür", "Tarih", "Coğrafya", "Bilim ve Uzay",
            "Sanat ve Edebiyat", "Spor", "Müzik", "Sinema", "Din Kültürü",
            "Matematik", "Teknoloji", "Doğa", "Türkçe", "Psikoloji", "Sağlık"
        )

        @Volatile private var INSTANCE: Prefs? = null
        fun get(context: Context): Prefs =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Prefs(context).also { INSTANCE = it } }
    }
}
