package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.util.Log
import com.emre.bilbakalim.arsiv.util.Importers
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

        // Bulanık kontrol: son kayıtlar + cevabı eksik olan bütün kayıtlar.
        //
        // İkincisi olmadan arşiv birkaç yüz soruyu geçtiğinde şu oluyordu:
        // aylar önce yakalanmış ama cevabı kaçmış bir soru yeniden çıkıyor,
        // OCR bir harfi farklı okuduğu için parmak izi tutmuyor, eski satır
        // da pencerenin dışında kaldığı için bulunamıyor — ikinci bir satır
        // açılıyor ve cevap ona yazılıyor. Eski satır sonsuza kadar "cevabı
        // eksik" olarak duruyordu. Artık o satır bulunup doldurulacak.
        //
        val probe = Probe(q, opts)
        for (old in similarityPool()) {
            if (!probe.matches(old)) continue
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
                    // Eksik şıklar tamamlanıyor — ama yeni liste o anki
                    // ekranın sırasıyla geliyor. Kayıtta zaten bir doğru
                    // cevap varsa sırası kayabilir; bu yüzden metnini
                    // tutup yeni listede yeniden arıyoruz. Yoksa doğru
                    // cevap sessizce yanlış şıkkı göstermeye başlıyordu.
                    val merged = old.copy(
                        optionA = opts.getOrNull(0) ?: old.optionA,
                        optionB = opts.getOrNull(1) ?: old.optionB,
                        optionC = opts.getOrNull(2) ?: old.optionC,
                        optionD = opts.getOrNull(3) ?: old.optionD
                    )
                    val remapped = TurkishText.matchIndex(merged.options, old.correctText)
                        ?: old.correctIndex
                    dao.update(merged.copy(correctIndex = remapped))
                }
            }
            return SaveResult.Duplicate(old.id)
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
     * Bulanık tekrar kontrolünün baktığı kayıtlar: son kayıtlar + cevabı
     * eksik olan bütün kayıtlar.
     */
    private suspend fun similarityPool(): List<QuestionEntity> =
        (dao.recent(RECENT_POOL) + dao.unanswered(UNANSWERED_POOL)).distinctBy { it.id }

    /**
     * "Bu soru zaten arşivde mi?" kararını veren kurallar.
     *
     * Tek yerde duruyor çünkü iki ayrı yol aynı kararı vermek zorunda:
     * ekrandan yakalama ([save]) ve yedekten içe aktarma ([importJson]).
     * Aranan metin için gereken hesaplar bir kez yapılıp saklanıyor —
     * karşılaştırma yüzlerce kayıt üzerinde dönüyor.
     */
    private class Probe(private val question: String, private val options: List<String>) {
        private val key = TurkishText.normalizeKey(question)
        private val optKey =
            options.map { TurkishText.normalizeKey(it) }.sorted().joinToString("|")
        private val negation = TurkishText.negationSignature(question)

        fun matches(old: QuestionEntity): Boolean {
            // Olumsuzluk farkı varsa hiçbir benzerlik ölçüsü bunları
            // birleştiremez — zıt anlamlı iki ayrı sorudur.
            if (TurkishText.negationSignature(old.questionText) != negation) return false

            val oldKey = TurkishText.normalizeKey(old.questionText)
            val sim = TurkishText.similarity(old.questionText, question)

            val optionsMatch = options.size >= 3 && old.options.size == options.size &&
                old.options.map { TurkishText.normalizeKey(it) }.sorted()
                    .joinToString("|") == optKey

            // Yarım yakalanmış okuma ("…kaç" ile "…kaç adettir?"). Bir sorunun
            // metninin başka bir soruda geçmesi onu aynı soru yapmaz; bu yüzden
            // hem uzunluklar birbirine çok yakın olmalı hem de ya şıklar birebir
            // aynı olmalı ya da fark çok küçük olmalı.
            val lengthRatio = minOf(oldKey.length, key.length).toFloat() /
                maxOf(oldKey.length, key.length).coerceAtLeast(1)
            val contained = oldKey.length >= 12 && key.length >= 12 &&
                (key.contains(oldKey) || oldKey.contains(key)) &&
                (optionsMatch && lengthRatio >= 0.60f || lengthRatio >= 0.85f)

            // Dört şıkkın tamamı birebir aynıysa neredeyse kesinlikle aynı
            // sorudur. Metnin başına "17. Süre Bitti" gibi bir fazlalık
            // yapışıp üstüne bir de OCR harf hatası olunca ne kapsama ne
            // benzerlik tutuyordu; şıklar bu ikisini de kurtarıyor.
            // Dört şık birebir aynı olsa bile metinler birbirinden çok
            // farklıysa ayrı sorulardır ("Hangisi X'tir?" / "Hangisi X
            // değildir?" aynı şıkları paylaşabiliyor). Bu yüzden eşik yüksek.
            val sameOptions = optionsMatch && sim >= 0.80f

            return contained || sameOptions || sim >= 0.92f
        }
    }

    // --- İçe aktarma ---------------------------------------------------------

    sealed interface ImportResult {
        /** [total] dosyadaki okunabilir satır sayısı. */
        data class Ok(
            val total: Int,
            val added: Int,
            val merged: Int,
            val skipped: Int
        ) : ImportResult

        data class Failed(val reason: String) : ImportResult
    }

    /**
     * JSON yedeğini arşive katar.
     *
     * Var olanın üstüne yazmaz, ekler: aynı soru zaten arşivdeyse yalnızca
     * eksikleri tamamlanır (bilinmeyen cevap, eksik şık, boş kategori).
     * Sayaçlarda büyük olan alındığı için aynı dosyayı iki kez içe aktarmak
     * hiçbir şeyi bozmaz — ikinci seferde her şey "değişmedi" diye geçer.
     */
    suspend fun importJson(text: String): ImportResult {
        val rows = try {
            Importers.parse(text)
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed(e.message ?: "Dosya okunamadı")
        }
        if (rows.isEmpty()) return ImportResult.Failed("Dosyada okunabilir soru yok")

        var added = 0
        var merged = 0
        var skipped = 0

        for (row in rows) {
            val incoming = Importers.toEntity(row)
            val existing = dao.byFingerprint(incoming.fingerprint)
                ?: Probe(row.question, row.options).let { probe ->
                    similarityPool().firstOrNull { probe.matches(it) }
                }

            if (existing == null) {
                if (dao.insertIgnore(incoming) > 0) added++ else skipped++
                continue
            }

            val updated = Importers.merge(existing, row)
            if (updated == existing) {
                skipped++
                continue
            }
            // Şıklar tamamlandıysa parmak izi de değişir; o parmak izi başka
            // bir satırda duruyorsa tekil indeks yazmayı reddeder. Böyle bir
            // durumda kaydı eski parmak iziyle güncelliyoruz: birleşmenin
            // geri kalanı yine de kazanç.
            val ok = runCatching { dao.update(updated) }.isSuccess
            if (!ok) {
                runCatching { dao.update(updated.copy(fingerprint = existing.fingerprint)) }
            }
            merged++
        }

        Log.i(TAG, "İçe aktarma: $added yeni, $merged birleşti, $skipped değişmedi")
        return ImportResult.Ok(rows.size, added, merged, skipped)
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
     *
     * [correctIndex] **ekrandaki** sıradır, kayıttaki değil. Oyun şıkları her
     * turda karıştırdığı için bu iki sıra birbirini tutmaz: ilk karşılaşmada
     * 1. sırada duran şık ikinci karşılaşmada 3. sırada olabilir. Bu yüzden
     * sırayı değil, o sıradaki **metni** alıp kayıttaki listede arıyoruz.
     * [screenOptions] boş geçilirse (ya da eşleşme bulunamazsa) sıra olduğu
     * gibi kullanılır; bu yalnızca ilk kayıtta güvenlidir, orada iki liste
     * zaten aynıdır.
     *
     * Cevabı ilk karşılaşmada yakalayamamış olsak bile bu çağrı sonraki
     * karşılaşmada aynı satırı doldurur; soru bir daha "cevabı eksik"
     * görünmez.
     */
    suspend fun recordReveal(
        id: Long,
        correctIndex: Int,
        screenOptions: List<String> = emptyList(),
        userWasRight: Boolean,
        countAsAttempt: Boolean,
        source: String = "renk"
    ) {
        if (correctIndex !in 0..3) return
        val row = dao.byId(id) ?: return

        val stored = TurkishText.matchIndex(row.options, screenOptions.getOrNull(correctIndex))
            ?: correctIndex
        if (stored !in row.options.indices) {
            Log.w(TAG, "Cevap #$id yazılamadı: şık listesi tutmuyor")
            return
        }

        if (!row.edited) dao.setCorrect(id, stored, source)
        if (countAsAttempt) dao.recordAttempt(id, if (userWasRight) 1 else 0)
        Log.i(TAG, "Cevap #$id -> ${'A' + stored}, kullanıcı ${if (userWasRight) "bildi" else "bilemedi"}")
    }

    /**
     * Arşivdeki doğru cevabın **o anki ekrandaki** sırası.
     *
     * Otomatik mod bunu kullanıyor: soruyu daha önce görmüşsek rastgele
     * seçmek yerine doğru şıkka basıyoruz. Şıklar karıştığı için kayıttaki
     * sıra doğrudan kullanılamaz — kayıttaki doğru cevabın metnini alıp
     * ekrandaki listede arıyoruz. Soru arşivde yoksa, cevabı henüz
     * bilinmiyorsa ya da metin ekrandakilerin hiçbirine benzemiyorsa null
     * döner ve seçim rastgele yapılır.
     */
    suspend fun knownAnswerOnScreen(id: Long, screenOptions: List<String>): Int? {
        val text = dao.byId(id)?.correctText ?: return null
        return TurkishText.matchIndex(screenOptions, text)
    }

    suspend fun updateManual(q: QuestionEntity) = dao.update(q.copy(edited = true))
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun allForExport() = dao.allForExport()

    companion object {
        private const val TAG = "SoruArsivi/Repo"
        /** Bulanık tekrar kontrolünün baktığı son kayıt sayısı. */
        private const val RECENT_POOL = 300
        /** Buna ek olarak bakılan, cevabı eksik kayıt sayısı. */
        private const val UNANSWERED_POOL = 400
        @Volatile private var INSTANCE: Repo? = null
        fun get(context: Context): Repo =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Repo(context).also { INSTANCE = it } }
    }
}
