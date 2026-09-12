package com.emre.bilbakalim.arsiv.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emre.bilbakalim.arsiv.capture.CaptureAccessibilityService
import com.emre.bilbakalim.arsiv.capture.ProjectionPermissionActivity
import com.emre.bilbakalim.arsiv.capture.ProjectionService
import com.emre.bilbakalim.arsiv.data.CategoryCount
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import com.emre.bilbakalim.arsiv.data.Repo
import com.emre.bilbakalim.arsiv.util.Exporters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val paket: String, val etiket: String, val ikon: Drawable?)

@OptIn(ExperimentalCoroutinesApi::class)
class ArsivViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs.get(app)
    private val repo = Repo.get(app)

    val settings: StateFlow<Prefs.Settings> = prefs.state

    val total: StateFlow<Int> =
        repo.totalCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val answered: StateFlow<Int> =
        repo.answeredCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val categories: StateFlow<List<CategoryCount>> =
        repo.categoryCounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent: StateFlow<List<QuestionEntity>> =
        repo.recent(12).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceRunning: StateFlow<Boolean> = CaptureAccessibilityService.running

    /** Ekran yansıtma açık mı — hızlı yakalama buna bağlı. */
    val fastCaptureOn: StateFlow<Boolean> = ProjectionService.running

    /** Son taramaların tek satırlık özeti (Teşhis ekranı). */
    val scanLog: StateFlow<List<String>> = CaptureAccessibilityService.scanLog

    fun clearScanLog() { CaptureAccessibilityService.scanLog.value = emptyList() }

    /** Tarama geçmişini düz metin olarak paylaşır — tanı için dışarı aktarmak kolay olsun. */
    fun shareScanLog(context: Context) {
        val text = scanLog.value.joinToString("\n").ifBlank { "Kayıt yok." }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Soru Arşivi — tarama geçmişi")
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Tarama geçmişini paylaş"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // --- Liste ekranı filtreleri -------------------------------------------
    val query = MutableStateFlow("")
    val filterCategory = MutableStateFlow<String?>(null)
    val onlyUnanswered = MutableStateFlow(false)

    val results: StateFlow<List<QuestionEntity>> =
        combine(query, filterCategory, onlyUnanswered) { q, c, u -> Triple(q, c, u) }
            .flatMapLatest { (q, c, u) -> repo.search(q, c, u) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeQuestion(id: Long) = repo.observeById(id)

    // --- Eylemler -----------------------------------------------------------
    fun setPaused(v: Boolean) = prefs.setPaused(v)
    fun setCategory(v: String) = prefs.setCategory(v)
    fun setTargets(v: Set<String>) = prefs.setTargets(v)
    fun setAutoDetectCategory(v: Boolean) = prefs.setAutoDetectCategory(v)
    fun setOcrFallback(v: Boolean) = prefs.setOcrFallback(v)
    fun setOcrAlways(v: Boolean) = prefs.setOcrAlways(v)
    fun setDetectAnswer(v: Boolean) = prefs.setDetectAnswer(v)
    fun setSaveScreenshots(v: Boolean) = prefs.setSaveScreenshots(v)
    fun setMinConfidence(v: Float) = prefs.setMinConfidence(v)
    fun setRequireQuestionShape(v: Boolean) = prefs.setRequireQuestionShape(v)
    fun setRequireFourOptions(v: Boolean) = prefs.setRequireFourOptions(v)
    fun setAutoPlay(v: Boolean) = prefs.setAutoPlay(v)
    fun setAutoAnswerDelay(ms: Long) = prefs.setAutoAnswerDelay(ms)
    fun setAutoRestart(v: Boolean) = prefs.setAutoRestart(v)
    fun setAutoUseKnownAnswer(v: Boolean) = prefs.setAutoUseKnownAnswer(v)
    fun setOnboarded(v: Boolean) = prefs.setOnboarded(v)
    fun setRegions(qt: Float, qb: Float, ot: Float, ob: Float) = prefs.setRegions(qt, qb, ot, ob)
    fun resetRegions() = prefs.resetRegions()

    fun updateQuestion(q: QuestionEntity) = viewModelScope.launch { repo.updateManual(q) }
    fun deleteQuestion(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    fun export(context: Context, format: Exporters.Format, onDone: (File?) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = repo.allForExport()
                    if (rows.isEmpty()) null else Exporters.write(context, rows, format)
                }.getOrNull()
            }
            onDone(file)
        }
    }

    // --- Cihaz durumu -------------------------------------------------------
    suspend fun installedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == getApplication<Application>().packageName) return@mapNotNull null
                AppInfo(
                    paket = pkg,
                    etiket = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg),
                    ikon = runCatching { ri.loadIcon(pm) }.getOrNull()
                )
            }
            .distinctBy { it.paket }
            .sortedBy { it.etiket.lowercase() }
    }

    companion object {
        /** Erişilebilirlik servisi sistem ayarlarında açık mı? */
        fun accessibilityEnabled(context: Context): Boolean {
            val cn = ComponentName(context, CaptureAccessibilityService::class.java)
            val flat = cn.flattenToString()
            val short = cn.flattenToShortString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(flat, true) || it.equals(short, true) }
        }

        /** Ekran yansıtma izni ister; sistem bir onay penceresi gösterir. */
        fun startFastCapture(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, ProjectionPermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        fun stopFastCapture(context: Context) = ProjectionService.stop(context)

        fun openAccessibilitySettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
