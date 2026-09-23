package com.emre.bilbakalim.arsiv.ui

import android.app.Application
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** Arkada tutulan toplam satır sayısı — akış yalnızca pencereyi taşıyor. */
    val scanLogTotal: StateFlow<Int> = CaptureAccessibilityService.scanLogTotal

    fun clearScanLog() = CaptureAccessibilityService.clearLog()

    /** Bu oturumda bildiremediğimiz sorular (Hatalar ekranı). */
    val misses: StateFlow<List<CaptureAccessibilityService.Miss>> =
        CaptureAccessibilityService.misses

    fun clearMisses() = CaptureAccessibilityService.clearMisses()

    /**
     * Tarama geçmişini **dosya olarak** paylaşır.
     *
     * Eskiden metin, paylaşım niyetinin içine (EXTRA_TEXT) konuyordu. Niyet
     * süreçler arasında ~1 MB'lık bir tampondan geçiyor ve metin orada
     * karakter başına iki bayt tutuyor: günlük birkaç bin satıra ulaşınca
     * startActivity TransactionTooLargeException fırlatıyor, runCatching onu
     * yutuyor ve düğme hiçbir şey yapmıyormuş gibi görünüyordu. "Temizle"den
     * sonra yeniden çalışmasının sebebi günlüğün küçülmesiydi. Dosya
     * niyetin içinden geçmiyor; yalnızca adresi geçiyor.
     */
    fun shareScanLog(context: Context) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val text = CaptureAccessibilityService.logSnapshot()
                        .joinToString("\n").ifBlank { "Kayıt yok." }
                    val dir = File(context.cacheDir, "teshis").apply { mkdirs() }
                    // Önceki paylaşımların dosyaları birikmesin.
                    dir.listFiles()?.forEach { it.delete() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    File(dir, "tarama_gecmisi_$stamp.txt").apply { writeText(text, Charsets.UTF_8) }
                }.getOrNull()
            }
            val ok = file != null && runCatching {
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Soru Arşivi — tarama geçmişi")
                    // Okuma izni seçici ekranından hedef uygulamaya ancak
                    // ClipData üzerinden geçiyor.
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(send, "Tarama geçmişini paylaş")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            // Artık sessizce yutulmuyor: paylaşım açılmadıysa bunu gör.
            if (!ok) Toast.makeText(context, "Tarama geçmişi paylaşılamadı", Toast.LENGTH_LONG).show()
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
    fun setFindOptionBoxes(v: Boolean) = prefs.setFindOptionBoxes(v)
    fun setSaveFailedFrames(v: Boolean) = prefs.setSaveFailedFrames(v)
    fun setRequireQuestionShape(v: Boolean) = prefs.setRequireQuestionShape(v)
    fun setRequireFourOptions(v: Boolean) = prefs.setRequireFourOptions(v)
    fun setAutoPlay(v: Boolean) = prefs.setAutoPlay(v)
    fun setAutoAnswerDelay(ms: Long) = prefs.setAutoAnswerDelay(ms)
    fun setAutoRestart(v: Boolean) = prefs.setAutoRestart(v)
    fun setAutoUseKnownAnswer(v: Boolean) = prefs.setAutoUseKnownAnswer(v)
    fun setAutoRandomWhenUnknown(v: Boolean) = prefs.setAutoRandomWhenUnknown(v)
    fun setUnknownChime(v: Boolean) = prefs.setUnknownChime(v)
    fun setOnboarded(v: Boolean) = prefs.setOnboarded(v)
    fun setRegions(qt: Float, qb: Float, ot: Float, ob: Float) = prefs.setRegions(qt, qb, ot, ob)
    fun resetRegions() = prefs.resetRegions()

    fun updateQuestion(q: QuestionEntity) = viewModelScope.launch { repo.updateManual(q) }
    fun deleteQuestion(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    /**
     * Seçilen dosyadaki yedeği arşive katar.
     *
     * Dosya kullanıcının seçtiği herhangi bir yerde olabilir (indirilenler,
     * bulut sürücüsü, mesajlaşma uygulaması); bu yüzden yolu değil içerik
     * çözücüyü kullanıyoruz.
     */
    fun importFrom(context: Context, uri: Uri, onDone: (Repo.ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
                when {
                    text == null -> Repo.ImportResult.Failed("Dosya açılamadı")
                    text.isBlank() -> Repo.ImportResult.Failed("Dosya boş")
                    else -> runCatching { repo.importJson(text) }
                        .getOrElse { Repo.ImportResult.Failed(it.message ?: "Okunamadı") }
                }
            }
            onDone(result)
        }
    }

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
