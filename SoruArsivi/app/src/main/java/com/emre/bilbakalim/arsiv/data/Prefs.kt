package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayMode {
    MANUAL, // Sadece soruları kaydeder, ekrana dokunmaz
    AUTO    // Bilineni doğru tıklar, yenileri dener, öğrenir, oyun bitince 'Yeni Oyun'a basar
}

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("arsiv_prefs", Context.MODE_PRIVATE)

    private val _playMode = MutableStateFlow(getPlayMode())
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _clickDelayMs = MutableStateFlow(getClickDelayMs())
    val clickDelayMs: StateFlow<Long> = _clickDelayMs.asStateFlow()

    private val _autoRestartGame = MutableStateFlow(isAutoRestartGame())
    val autoRestartGame: StateFlow<Boolean> = _autoRestartGame.asStateFlow()

    fun getPlayMode(): PlayMode {
        val modeStr = sp.getString(KEY_PLAY_MODE, PlayMode.AUTO.name) ?: PlayMode.AUTO.name
        return try {
            PlayMode.valueOf(modeStr)
        } catch (e: Exception) {
            PlayMode.AUTO
        }
    }

    fun setPlayMode(mode: PlayMode) {
        sp.edit().putString(KEY_PLAY_MODE, mode.name).apply()
        _playMode.value = mode
    }

    fun getClickDelayMs(): Long {
        return sp.getLong(KEY_CLICK_DELAY_MS, 1200L)
    }

    fun setClickDelayMs(delayMs: Long) {
        val safeDelay = delayMs.coerceIn(200L, 10000L)
        sp.edit().putLong(KEY_CLICK_DELAY_MS, safeDelay).apply()
        _clickDelayMs.value = safeDelay
    }

    fun isAutoRestartGame(): Boolean {
        return sp.getBoolean(KEY_AUTO_RESTART_GAME, true)
    }

    fun setAutoRestartGame(enabled: Boolean) {
        sp.edit().putBoolean(KEY_AUTO_RESTART_GAME, enabled).apply()
        _autoRestartGame.value = enabled
    }

    companion object {
        private const val KEY_PLAY_MODE = "key_play_mode"
        private const val KEY_CLICK_DELAY_MS = "key_click_delay_ms"
        private const val KEY_AUTO_RESTART_GAME = "key_auto_restart_game"
    }
}
