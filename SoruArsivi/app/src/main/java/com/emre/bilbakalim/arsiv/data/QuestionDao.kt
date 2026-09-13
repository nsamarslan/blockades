package com.emre.bilbakalim.arsiv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String?, val adet: Int)

/**
 * Tekrar kontrolü için gereken en az alan.
 *
 * Bütün arşivi tam kayıt olarak çekmek pahalı; karşılaştırma yalnızca soru
 * metniyle şıklara bakıyor. Eşleşme bulunduğunda tam kayıt tek tek okunuyor.
 */
data class DedupRow(
    val id: Long,
    val questionText: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?
) {
    val options: List<String> get() = listOfNotNull(optionA, optionB, optionC, optionD)
}

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(q: QuestionEntity): Long

    @Update
    suspend fun update(q: QuestionEntity)

    @Query("SELECT * FROM questions WHERE fingerprint = :fp LIMIT 1")
    suspend fun byFingerprint(fp: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<QuestionEntity?>

    @Query("UPDATE questions SET seenCount = seenCount + 1 WHERE id = :id")
    suspend fun bumpSeen(id: Long)

    @Query("UPDATE questions SET correctIndex = :idx, answerSource = :src WHERE id = :id AND edited = 0")
    suspend fun setCorrect(id: Long, idx: Int, src: String)

    /** Bir deneme kaydeder; [inc] doğru bildiysen 1, bildiremediysen 0. */
    @Query("UPDATE questions SET answeredCount = answeredCount + 1, correctCount = correctCount + :inc WHERE id = :id")
    suspend fun recordAttempt(id: Long, inc: Int)

    @Query("UPDATE questions SET questionText = :text WHERE id = :id AND edited = 0")
    suspend fun replaceText(id: Long, text: String)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    /** Son N kaydın metni — bulanık tekrar kontrolü için. */
    @Query("SELECT * FROM questions ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<QuestionEntity>

    /**
     * Bulanık tekrar kontrolünün karşılaştıracağı kayıtlar — **tüm arşiv**.
     *
     * Eskiden yalnızca son birkaç yüz kayda bakılıyordu ve bu, OCR'ın bir
     * harfi yanlış okuduğu her soruda ikinci bir satır açılmasına yol
     * açıyordu: "…sönen yildza ne ad verilir?" ile "…sönen yıldıza ne ad
     * verilir?" %97 benzer, yani eşleşme kuralı bunu rahatça yakalıyor —
     * ama eski satır pencerenin dışında kaldığı için hiç karşılaştırılmıyordu.
     *
     * Yalnızca karşılaştırma için gereken sütunlar okunuyor; binlerce satır
     * için bile bu birkaç milisaniye sürüyor ve karşılaştırmanın kendisi
     * uzunluk süzgeciyle zaten hızla eleniyor.
     */
    @Query(
        """
        SELECT id, questionText, optionA, optionB, optionC, optionD FROM questions
        ORDER BY capturedAt DESC LIMIT :limit
        """
    )
    suspend fun dedupCandidates(limit: Int): List<DedupRow>

    @Query("SELECT * FROM questions ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE correctIndex IS NOT NULL")
    fun observeAnswered(): Flow<Int>

    @Query("SELECT category, COUNT(*) AS adet FROM questions GROUP BY category ORDER BY adet DESC")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query(
        """
        SELECT * FROM questions
        WHERE (:q = '' OR questionText LIKE '%' || :q || '%'
               OR optionA LIKE '%' || :q || '%' OR optionB LIKE '%' || :q || '%'
               OR optionC LIKE '%' || :q || '%' OR optionD LIKE '%' || :q || '%')
          AND (:cat IS NULL OR category = :cat)
          AND (:onlyUnanswered = 0 OR correctIndex IS NULL)
        ORDER BY capturedAt DESC
        """
    )
    fun search(q: String, cat: String?, onlyUnanswered: Int): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions ORDER BY capturedAt ASC")
    suspend fun allForExport(): List<QuestionEntity>
}
