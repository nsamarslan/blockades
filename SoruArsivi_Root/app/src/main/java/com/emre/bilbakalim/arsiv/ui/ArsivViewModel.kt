package com.emre.bilbakalim.arsiv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emre.bilbakalim.arsiv.data.PlayMode
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import com.emre.bilbakalim.arsiv.data.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArsivViewModel(
    private val repo: Repo,
    private val prefs: Prefs
) : ViewModel() {

    val playMode: StateFlow<PlayMode> = prefs.playMode
    val clickDelayMs: StateFlow<Long> = prefs.clickDelayMs
    val autoRestartGame: StateFlow<Boolean> = prefs.autoRestartGame

    val questions: StateFlow<List<QuestionEntity>> = repo.getAllQuestionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalQuestionsCount: StateFlow<Int> = repo.getQuestionsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setPlayMode(mode: PlayMode) {
        viewModelScope.launch { prefs.setPlayMode(mode) }
    }

    fun setClickDelayMs(delayMs: Long) {
        viewModelScope.launch { prefs.setClickDelayMs(delayMs) }
    }

    fun setAutoRestartGame(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoRestartGame(enabled) }
    }

    fun deleteQuestion(question: QuestionEntity) {
        viewModelScope.launch { repo.deleteQuestion(question) }
    }

    fun clearAllQuestions() {
        viewModelScope.launch { repo.clearAll() }
    }

    companion object {
        fun provideFactory(repo: Repo, prefs: Prefs): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ArsivViewModel(repo, prefs) as T
                }
            }
    }
}
