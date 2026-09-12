package com.emre.bilbakalim.arsiv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String?, val adet: Int)

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
