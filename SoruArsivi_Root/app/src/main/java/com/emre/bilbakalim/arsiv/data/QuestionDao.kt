package com.emre.bilbakalim.arsiv.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY id DESC")
    fun getAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions")
    fun getQuestionsCount(): Flow<Int>

    @Query("SELECT * FROM questions WHERE normalizedQuestion = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity): Long

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM questions")
    suspend fun clearAll()
}
