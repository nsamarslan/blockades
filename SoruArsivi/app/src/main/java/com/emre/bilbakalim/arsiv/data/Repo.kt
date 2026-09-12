package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.util.Log
import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlinx.coroutines.flow.Flow

/**
 * Kaydetme mantığının tek kapısı. Aynı sorunun tekrar tekrar yazılmasını
 * iki aşamada engeller:
 *   1. Parmak izi (kesin eşleşme, veritabanı seviyesinde tekil indeks)
 *   2. Bulanık benzerlik (OCR bir iki harfi yanlış okuduysa)
 */
class Repo private constructor(context: Context) {

    private val dao = ArsivDatabase.get(context).questionDao()

    val totalCount: Flow<Int> = dao.observeTotal()
    val answeredCount: Flow<Int> = dao.observeAnswered()
    val categoryCounts: Flow<List<CategoryCount>> = dao.observeCategoryCounts()
    fun recent(limit: Int = 30): Flow<List<QuestionEntity>> = dao.observeRecent(limit)
    fun observeById(id: Long) = dao.observeById(id)
    fun search(q: String, cat: String?, onlyUnanswered: Boolean) =
        dao.search(q, cat, if (onlyUnanswered) 1 else 0)

    sealed interface SaveResult {
        data class Inserted(val id: Long) : SaveResult
        data class Duplicate(val id: Long) : SaveResult
        data object Rejected : SaveResult
    }

    suspend fun save(
        question: String,
        options: List<String>,
        category: String?,
        source: CaptureSource,
        confidence: Float,
        screenshotPath: String?
    ): SaveResult {
        val q = TurkishText.cleanOcr(question)
        val opts = options.map { TurkishText.cleanOcr(TurkishText.stripOptionPrefix(it)) }
            .filter { it.isNotBlank() }

        if (q.length < 8 || opts.size < 2) return SaveResult.Rejected

        val fp = TurkishText.fingerprint(q, opts)

        dao.byFingerprint(fp)?.let { return SaveResult.Duplicate(it.id) }

        // Bulanık kontrol: son 300 kayıtla karşılaştır.
        //
        // İki ayrı kusuru birden yakalıyoruz:
        //  1. OCR bir iki harfi yanlış okudu  -> benzerlik ölçüsü
        //  2. Soru ekrana yazılırken yarım yakalandı ("...kaç" / "...kaç adettir?")
        //     ya da üstüne "Süre Bitti" gibi bir kelime bindi -> kapsama ölçüsü
        // İkincisi olmadan aynı soru iki ayrı kayıt olarak duruyordu.
        val newKey = TurkishText.normalizeKey(q)
        val newOptKey = opts.map { TurkishText.normalizeKey(it) }.sorted().joinToString("|")
        val newNeg = TurkishText.negationSignature(q)

        for (old in dao.recent(300)) {
            // Olumsuzluk farkı varsa hiçbir benzerlik ölçüsü bunları
            // birleştiremez — zıt anlamlı iki ayrı sorudur.
            if (TurkishText.negationSignature(old.questionText) != newNeg) continue

            val oldKey = TurkishText.normalizeKey(old.questionText)
            val sim = TurkishText.similarity(old.questionText, q)

            val optionsMatch = opts.size >= 3 && old.options.size == opts.size &&
                old.options.map { TurkishText.normalizeKey(it) }.sorted()
                    .joinToString("|") == newOptKey

            // Yarım yakalanmış okuma ("…kaç" ile "…kaç adettir?"). Bir sorunun
            // metninin başka bir soruda geçmesi onu aynı soru yapmaz; bu yüzden
            // hem uzunluklar birbirine çok yakın olmalı hem de ya şıklar birebir
            // aynı olmalı ya da fark çok küçük olmalı.
            val lengthRatio = minOf(oldKey.length, newKey.length).toFloat() /
                maxOf(oldKey.length, newKey.length).coerceAtLeast(1)
            val contained = oldKey.length >= 12 && newKey.length >= 12 &&
                (newKey.contains(oldKey) || oldKey.contains(newKey)) &&
                (optionsMatch && lengthRatio >= 0.60f || lengthRatio >= 0.85f)

            // Dört şıkkın tamamı birebir aynıysa neredeyse kesinlikle aynı
            // sorudur. Metnin başına "17. Süre Bitti" gibi bir fazlalık
            // yapışıp üstüne bir de OCR harf hatası olunca ne kapsama ne
            // benzerlik tutuyordu; şıklar bu ikisini de kurtarıyor.
            // Dört şık birebir aynı olsa bile metinler birbirinden çok
            // farklıysa ayrı sorulardır ("Hangisi X'tir?" / "Hangisi X
            // değildir?" aynı şıkları paylaşabiliyor). Bu yüzden eşik yüksek.
            val sameOptions = optionsMatch && sim >= 0.80f

            if (contained || sameOptions || sim >= 0.92f) {
                if (!old.edited) {
                    // Hangi metin daha temiz? Arayüz uyarısı içermeyen kazanır;
                    // ikisi de temizse daha uzun olanı alırız.
                    val oldDirty = TurkishText.hasChromePhrase(old.questionText)
                    val newDirty = TurkishText.hasChromePhrase(q)
                    val takeNew = when {
                        oldDirty && !newDirty -> true
                        !oldDirty && newDirty -> false
                        else -> q.length > old.questionText.length
                    }
                    if (takeNew && q != old.questionText) dao.replaceText(old.id, q)
                    if (old.options.size < opts.size) {
                        dao.update(
                            old.copy(
                                optionA = opts.getOrNull(0) ?: old.optionA,
                                optionB = opts.getOrNull(1) ?: old.optionB,
                                optionC = opts.getOrNull(2) ?: old.optionC,
                                optionD = opts.getOrNull(3) ?: old.optionD
                            )
                        )
                    }
                }
                return SaveResult.Duplicate(old.id)
            }
        }

        val entity = QuestionEntity(
            questionText = q,
            optionA = opts.getOrNull(0),
            optionB = opts.getOrNull(1),
            optionC = opts.getOrNull(2),
            optionD = opts.getOrNull(3),
            category = category?.takeIf { it.isNotBlank() },
            source = source.name,
            confidence = confidence,
            fingerprint = fp,
            screenshotPath = screenshotPath
        )
        val id = dao.insertIgnore(entity)
        return if (id > 0) {
            Log.i(TAG, "Yeni soru kaydedildi #$id: ${q.take(50)}")
            SaveResult.Inserted(id)
        } else {
            SaveResult.Duplicate(dao.byFingerprint(fp)?.id ?: -1L)
        }
    }

    /**
     * Yeni bir karşılaşmayı sayar.
     *
     * Bunu bilerek [save] dışına aldık: aynı soru ekranı saniyede birkaç kez
     * taranıyor ve her tarama küçük OCR farkları yüzünden ayrı bir kayıt
     * denemesi oluyordu. Sayaç orada artırılınca bir kez gördüğün soru
     * "3 kez çıktı" görünüyordu. Artık yalnızca gerçekten yeni bir soruya
     * geçildiğinde çağrılıyor.
     */
    suspend fun countEncounter(id: Long) = dao.bumpSeen(id)

    /**
     * Cevap açıldığında çağrılır. Doğru cevabı işler ve — süre dolmadıysa —
     * bunu bir "deneme" olarak sayıp doğru bilip bilmediğini kaydeder.
     * Böylece soru başına "kaç kez çıktı, kaçında bildin" çıkarılabiliyor.
     */
    suspend fun recordReveal(
        id: Long,
        correctIndex: Int,
        userWasRight: Boolean,
        countAsAttempt: Boolean,
        source: String = "renk"
    ) {
        if (correctIndex !in 0..3) return
        val row = dao.byId(id) ?: return
        if (!row.edited) dao.setCorrect(id, correctIndex, source)
        if (countAsAttempt) dao.recordAttempt(id, if (userWasRight) 1 else 0)
        Log.i(TAG, "Cevap #$id -> ${'A' + correctIndex}, kullanıcı ${if (userWasRight) "bildi" else "bilemedi"}")
    }

    suspend fun updateManual(q: QuestionEntity) = dao.update(q.copy(edited = true))
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun allForExport() = dao.allForExport()

    companion object {
        private const val TAG = "SoruArsivi/Repo"
        @Volatile private var INSTANCE: Repo? = null
        fun get(context: Context): Repo =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Repo(context).also { INSTANCE = it } }
    }
}
