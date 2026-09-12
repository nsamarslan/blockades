package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlinx.coroutines.flow.Flow

class Repo(private val dao: QuestionDao) {

    fun getAllQuestionsFlow(): Flow<List<QuestionEntity>> = dao.getAllQuestions()
    fun getQuestionsCountFlow(): Flow<Int> = dao.getQuestionsCount()

    suspend fun findQuestionByNormalizedText(normalizedQ: String): QuestionEntity? {
        return dao.findByNormalized(normalizedQ)
    }

    suspend fun saveOrUpdateQuestion(entity: QuestionEntity) {
        val normalized = TurkishText.normalize(entity.question)
        val existing = dao.findByNormalized(normalized)
        if (existing != null) {
            val updatedCorrect = entity.correctAnswer ?: existing.correctAnswer
            val updatedOptions = if (entity.options.isNotEmpty()) entity.options else existing.options
            val updated = existing.copy(
                correctAnswer = updatedCorrect,
                options = updatedOptions,
                timesSeen = existing.timesSeen + 1,
                timestamp = System.currentTimeMillis()
            )
            dao.update(updated)
        } else {
            val newEntity = entity.copy(normalizedQuestion = normalized)
            dao.insert(newEntity)
        }
    }

    suspend fun deleteQuestion(entity: QuestionEntity) = dao.delete(entity)
    suspend fun clearAll() = dao.clearAll()
}
