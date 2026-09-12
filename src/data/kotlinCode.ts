export const kotlinFiles = [
  {
    fileName: "Prefs.kt",
    path: "app/src/main/java/com/emre/bilbakalim/arsiv/data/Prefs.kt",
    description: "Manuel/Otomatik mod, tıklama gecikmesi (ms) ve otomatik yeni oyun ayarlarını tutan SharedPreferences yöneticisi",
    code: `package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayMode {
    MANUAL, // Sadece soruları kaydeder, tıklama yapmaz
    AUTO    // Bilineni doğru yanıtlar, bilinmeyeni rastgele tıklar, oyun bitince 'Yeni Oyun'a basar
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
        val modeStr = sp.getString(KEY_PLAY_MODE, PlayMode.MANUAL.name) ?: PlayMode.MANUAL.name
        return try {
            PlayMode.valueOf(modeStr)
        } catch (e: Exception) {
            PlayMode.MANUAL
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
}`
  },
  {
    fileName: "CaptureAccessibilityService.kt",
    path: "app/src/main/java/com/emre/bilbakalim/arsiv/capture/CaptureAccessibilityService.kt",
    description: "Otomatik soru algılama, veritabanından doğru cevabı arama, tıklama simülasyonu ve 'Yeni Oyun' butonuna basma motoru",
    code: `package com.emre.bilbakalim.arsiv.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.emre.bilbakalim.arsiv.ArsivApp
import com.emre.bilbakalim.arsiv.data.PlayMode
import com.emre.bilbakalim.arsiv.data.QuestionEntity
import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlinx.coroutines.*
import kotlin.random.Random

class CaptureAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val prefs by lazy { ArsivApp.instance.prefs }
    private val repo by lazy { ArsivApp.instance.repo }

    // Otomasyon durum takibi
    private var lastHandledQuestion: String = ""
    private var isAnsweringInProgress = false
    private var lastRestartClickTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val root = rootInActiveWindow ?: return
        val currentMode = prefs.getPlayMode()

        // 1. Oyun Sonu Ekranı Kontrolü: "Yeni Oyun" butonu
        if (currentMode == PlayMode.AUTO && prefs.isAutoRestartGame()) {
            checkForGameOverAndRestart(root)
        }

        // 2. Soru ve Şıkları Tespit Et
        handleQuestionCaptureAndAutoPlay(root, currentMode)
    }

    /**
     * Ekranda "Yeni Oyun" veya benzeri oyun sonu butonu var mı kontrol eder,
     * varsa belirlenen gecikmeyle tıklar.
     */
    private fun checkForGameOverAndRestart(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastRestartClickTime < 3000L) return // Çok hızlı mükerrer tıklamayı önle

        // TRT Bil Bakalım oyun sonundaki "Yeni Oyun" butonunu ara
        val restartNodes = root.findAccessibilityNodeInfosByText("Yeni Oyun")
        if (!restartNodes.isNullOrEmpty()) {
            val buttonNode = restartNodes.firstOrNull() ?: return
            Log.d(TAG, "Oyun sonu tespit edildi! 'Yeni Oyun' butonuna tıklanıyor...")
            lastRestartClickTime = now
            
            val delay = prefs.getClickDelayMs()
            mainHandler.postDelayed({
                clickNodeOrCenter(buttonNode)
                lastHandledQuestion = "" // Yeni oyun başlayacağı için soru belleğini sıfırla
            }, delay)
            return
        }

        // Alternatif metinler (Tebrikler / Kaybettiniz durumunda buton)
        val endCheck = root.findAccessibilityNodeInfosByText("Tebrikler")
        if (!endCheck.isNullOrEmpty()) {
            // Ekranda Tebrikler var ama metin farklıysa alt butonları tara
            val allClickable = mutableListOf<AccessibilityNodeInfo>()
            findClickableButtons(root, allClickable)
            for (btn in allClickable) {
                val text = (btn.text ?: btn.contentDescription ?: "").toString()
                if (text.contains("Yeni Oyun", ignoreCase = true) || text.contains("Tekrar", ignoreCase = true)) {
                    lastRestartClickTime = now
                    mainHandler.postDelayed({
                        clickNodeOrCenter(btn)
                        lastHandledQuestion = ""
                    }, prefs.getClickDelayMs())
                    return
                }
            }
        }
    }

    /**
     * Soru ve şıkları algılar. Manuel modda sadece arşive kaydeder,
     * Otomatik modda veritabanını kontrol edip doğru cevaba veya rastgele şıkka tıklar.
     */
    private fun handleQuestionCaptureAndAutoPlay(root: AccessibilityNodeInfo, mode: PlayMode) {
        if (isAnsweringInProgress) return

        serviceScope.launch {
            try {
                // Ekrandaki tüm metinleri ve butonları topla
                val parsedQuestion = QuestionParser.parseFromRoot(root) ?: return@launch

                val questionText = parsedQuestion.question.trim()
                val options = parsedQuestion.options.map { it.trim() }

                // En az 2 seçenek olmalı ve soru metni geçerli olmalı
                if (questionText.length < 5 || options.size < 2) return@launch

                // Aynı soruyu tekrar tekrar cevaplamayı engelle
                val normalizedQ = TurkishText.normalize(questionText)
                if (normalizedQ == lastHandledQuestion) return@launch

                Log.d(TAG, "Yeni Soru Algılandı: $questionText, Şıklar: $options")

                // Veritabanında daha önce kaydedilmiş mi kontrol et
                val existingQuestion = repo.findQuestionByNormalizedText(normalizedQ)
                val knownAnswer = existingQuestion?.correctAnswer

                if (mode == PlayMode.MANUAL) {
                    // MANUEL MOD: Yalnızca kaydet veya güncelle, ekrana ASLA tıklama
                    repo.saveOrUpdateQuestion(
                        QuestionEntity(
                            question = questionText,
                            options = options,
                            correctAnswer = knownAnswer,
                            category = parsedQuestion.category
                        )
                    )
                    lastHandledQuestion = normalizedQ
                    return@launch
                }

                // OTOMATİK MOD:
                isAnsweringInProgress = true
                lastHandledQuestion = normalizedQ

                val delayMs = prefs.getClickDelayMs()
                Log.d(TAG, "Otomatik mod aktif. $delayMs ms sonra cevaplanacak...")
                delay(delayMs)

                // Tıklanacak şıkkı belirle
                val optionToClick: String
                val isKnown: Boolean

                if (!knownAnswer.isNullOrBlank() && options.any { TurkishText.isMatch(it, knownAnswer) }) {
                    // Veritabanında doğru cevap var!
                    optionToClick = options.first { TurkishText.isMatch(it, knownAnswer) }
                    isKnown = true
                    Log.d(TAG, "BİLİNEN SORU! Doğru cevap işaretleniyor: $optionToClick")
                } else {
                    // Yeni soru: Rastgele bir şık seç
                    optionToClick = options.random()
                    isKnown = false
                    Log.d(TAG, "YENİ SORU! Rastgele şık seçildi: $optionToClick")
                }

                // Şık butonunu ekranda bul ve tıkla
                withContext(Dispatchers.Main) {
                    val clicked = clickOptionOnScreen(root, optionToClick)
                    if (clicked) {
                        Log.d(TAG, "Şıkka başarıyla tıklandı: $optionToClick")
                    } else {
                        Log.w(TAG, "Şık düğümü doğrudan tıklanamadı, alternatif koordinat denenecek")
                    }
                }

                // Soru ve seçeneği ilk defa veritabanına ekle
                repo.saveOrUpdateQuestion(
                    QuestionEntity(
                        question = questionText,
                        options = options,
                        correctAnswer = if (isKnown) knownAnswer else null,
                        category = parsedQuestion.category
                    )
                )

                // Cevabın sonucunu (yeşil/kırmızı) öğrenmek için kısa bekleme
                delay(800L)
                isAnsweringInProgress = false

            } catch (e: Exception) {
                Log.e(TAG, "Oynatma hatası", e)
                isAnsweringInProgress = false
            }
        }
    }

    /**
     * Ekranda verilen metne sahip şık butonunu arar ve tıklar.
     */
    private fun clickOptionOnScreen(root: AccessibilityNodeInfo, targetText: String): Boolean {
        // Doğrudan metin ile ara
        val matches = root.findAccessibilityNodeInfosByText(targetText)
        if (!matches.isNullOrEmpty()) {
            for (node in matches) {
                if (clickNodeOrCenter(node)) return true
            }
        }

        // Kısmi veya normalleştirilmiş metin ile düğümleri tara
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        for (node in allNodes) {
            val text = (node.text ?: node.contentDescription ?: "").toString().trim()
            if (TurkishText.isMatch(text, targetText)) {
                if (clickNodeOrCenter(node)) return true
            }
        }

        return false
    }

    /**
     * Düğüm tıklanabilirse click aksiyonu gönderir;
     * tıklanamazsa ebeveynine bakar; hiçbiri olmazsa dispatchGesture ile koordinata dokunur!
     */
    private fun clickNodeOrCenter(node: AccessibilityNodeInfo): Boolean {
        // 1. Düğümün kendisi tıklanabilir mi?
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 2. Ebeveyn düğüm tıklanabilir mi?
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        // 3. Fallback: Ekrandaki koordinatlarına (x, y) dokunma simülasyonu (Gesture)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.centerX() > 0 && bounds.centerY() > 0) {
            return dispatchClickGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        return false
    }

    /**
     * Android Accessibility GestureDescription ile ekrandaki x, y koordinatına dokunur.
     */
    private fun dispatchClickGesture(x: Float, y: Float): Boolean {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            collectAllNodes(node.getChild(i), list)
        }
    }

    private fun findClickableButtons(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) list.add(node)
        for (i in 0 until node.childCount) {
            findClickableButtons(node.getChild(i), list)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "CaptureAccessibilityService kesintiye uğradı")
        isAnsweringInProgress = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "SoruArsiviBot"
    }
}`
  },
  {
    fileName: "SettingsScreen.kt",
    path: "app/src/main/java/com/emre/bilbakalim/arsiv/ui/SettingsScreen.kt",
    description: "Manuel / Otomatik Mod seçimi, el ile tıklama gecikmesi (ms) girişi ve hazır gecikme butonları içeren ayar ekranı",
    code: `package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ArsivViewModel) {
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val autoRestart by viewModel.autoRestartGame.collectAsState()

    var delayInputText by remember(clickDelayMs) { mutableStateOf(clickDelayMs.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bot ve Oyun Ayarları", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. OYUN MODU SEÇİCİ (Manuel vs Otomatik)
            Text(
                text = "ÇALIŞMA MODU",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Manuel Mod Butonu
                        val isManual = playMode == PlayMode.MANUAL
                        Button(
                            onClick = { viewModel.setPlayMode(PlayMode.MANUAL) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isManual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Manuel",
                                color = if (isManual) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Otomatik Mod Butonu
                        val isAuto = playMode == PlayMode.AUTO
                        Button(
                            onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAuto) Color(0xFF10B981) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Otomatik",
                                color = if (isAuto) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (playMode == PlayMode.MANUAL) {
                            "ℹ️ Manuel Mod: Ekrana hiçbir tıklama yapılmaz. Sadece soru ve şıkları yakalar, doğru cevabı veritabanına kaydeder."
                        } else {
                            "⚡ Otomatik Bot Modu: Bilinen soruları anında doğru işaretler. Yeni sorularda rastgele şık dener, doğru cevabı renkten öğrenir ve oyun bitince 'Yeni Oyun' butonuna basarak kesintisiz devam eder."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. TIKLAMA GECİKMESİ AYARI (ms)
            Text(
                text = "TIKLAMA GECİKMESİ (Gecikme Süresi)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Soru belirdikten sonra botun şıkkı tıklaması için geçecek süre (milisaniye cinsinden):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = delayInputText,
                        onValueChange = { input ->
                            delayInputText = input
                            val parsed = input.toLongOrNull()
                            if (parsed != null && parsed >= 200) {
                                viewModel.setClickDelayMs(parsed)
                            }
                        },
                        label = { Text("Gecikme (Milisaniye)") },
                        suffix = { Text("ms") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Hızlı Hazır Seçenek Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(500L to "Hızlı (0.5s)", 1200L to "Normal (1.2s)", 2500L to "Doğal (2.5s)").forEach { (ms, label) ->
                            FilterChip(
                                selected = clickDelayMs == ms,
                                onClick = {
                                    delayInputText = ms.toString()
                                    viewModel.setClickDelayMs(ms)
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. YENİ OYUN OTOMATİK DÖNGÜSÜ
            Text(
                text = "OYUN SONU VE TEKRAR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Otomatik 'Yeni Oyun' Başlat", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Oyun bitip skor ekranı ('Tebrikler' / '108-84') geldiğinde 'Yeni Oyun' butonuna otomatik basılsın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoRestart,
                        onCheckedChange = { viewModel.setAutoRestartGame(it) }
                    )
                }
            }
        }
    }
}`
  },
  {
    fileName: "HomeScreen.kt",
    path: "app/src/main/java/com/emre/bilbakalim/arsiv/ui/HomeScreen.kt",
    description: "Ana ekranda hızlı Manuel / Otomatik mod geçişi ve aktif durum göstergesi",
    code: `package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@Composable
fun HomeQuickModeCard(viewModel: ArsivViewModel) {
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val totalQuestions by viewModel.totalQuestionsCount.collectAsState()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (playMode == PlayMode.AUTO) Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (playMode == PlayMode.AUTO) Color(0xFF10B981) else Color(0xFFF59E0B),
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (playMode == PlayMode.AUTO) "OTOMATİK BOT AKTİF" else "MANUEL MOD AKTİF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (playMode == PlayMode.AUTO) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Toplam: $totalQuestions Soru",
                    fontSize = 12.sp,
                    color = if (playMode == PlayMode.AUTO) Color(0xFFA7F3D0) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // Hızlı Mod Değiştirme Butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setPlayMode(PlayMode.MANUAL) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (playMode == PlayMode.MANUAL) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manuel")
                }

                Button(
                    onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playMode == PlayMode.AUTO) Color(0xFF10B981) else Color(0xFF374151)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Otomatik Bot")
                }
            }

            if (playMode == PlayMode.AUTO) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "⚡ Tıklama hızı: \${clickDelayMs}ms | Bilinen sorular doğru cevaplanacak, yeni sorular rastgele denenip öğrenilecek, oyun bitince 'Yeni Oyun'a basılacak.",
                    fontSize = 11.sp,
                    color = Color(0xFFD1FAE5),
                    lineHeight = 16.sp
                )
            }
        }
    }
}`
  },
  {
    fileName: "ArsivViewModel.kt",
    path: "app/src/main/java/com/emre/bilbakalim/arsiv/ui/ArsivViewModel.kt",
    description: "Mod ve gecikme ayarlarını Flow üzerinden UI bileşenlerine ileten ViewModel güncellemeleri",
    code: `package com.emre.bilbakalim.arsiv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emre.bilbakalim.arsiv.data.PlayMode
import com.emre.bilbakalim.arsiv.data.Prefs
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

    val totalQuestionsCount: StateFlow<Int> = repo.getQuestionsCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setPlayMode(mode: PlayMode) {
        viewModelScope.launch {
            prefs.setPlayMode(mode)
        }
    }

    fun setClickDelayMs(delayMs: Long) {
        viewModelScope.launch {
            prefs.setClickDelayMs(delayMs)
        }
    }

    fun setAutoRestartGame(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoRestartGame(enabled)
        }
    }
}`
  }
];
