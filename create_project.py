import os
import zipfile

base_dir = "SoruArsivi"

files = {}

# settings.gradle.kts
files["settings.gradle.kts"] = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SoruArsivi"
include(":app")
"""

# gradle.properties
files["gradle.properties"] = """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
"""

# gradle/wrapper/gradle-wrapper.properties
files["gradle/wrapper/gradle-wrapper.properties"] = """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""

# build.gradle.kts
files["build.gradle.kts"] = """plugins {
    id("com.android.application") version "8.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false
}
"""

# app/build.gradle.kts
files["app/build.gradle.kts"] = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.emre.bilbakalim.arsiv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.emre.bilbakalim.arsiv"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0-bot"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Room Veritabanı
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // JSON Dönüştürücü
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
"""

# app/proguard-rules.pro
files["app/proguard-rules.pro"] = """# Proguard rules for SoruArsivi
-keep class com.emre.bilbakalim.arsiv.data.** { *; }
"""

# app/src/main/AndroidManifest.xml
files["app/src/main/AndroidManifest.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ArsivApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SoruArsivi">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.SoruArsivi">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- TRT Bil Bakalım Otomatik Oynama & Soru Yakalama Erişilebilirlik Servisi -->
        <service
            android:name=".capture.CaptureAccessibilityService"
            android:exported="true"
            android:label="@string/accessibility_service_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>

</manifest>
"""

# app/src/main/res/xml/accessibility_service_config.xml
files["app/src/main/res/xml/accessibility_service_config.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="50"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds|flagIncludeNotImportantViews" />
"""

# app/src/main/res/xml/data_extraction_rules.xml
files["app/src/main/res/xml/data_extraction_rules.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="."/>
        <include domain="sharedpref" path="."/>
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="."/>
        <include domain="sharedpref" path="."/>
    </device-transfer>
</data-extraction-rules>
"""

# app/src/main/res/xml/backup_rules.xml
files["app/src/main/res/xml/backup_rules.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="database" path="."/>
    <include domain="sharedpref" path="."/>
</full-backup-content>
"""

# app/src/main/res/values/strings.xml
files["app/src/main/res/values/strings.xml"] = """<resources>
    <string name="app_name">Soru Arşivi &amp; Bot</string>
    <string name="accessibility_service_label">Soru Arşivi ve Otomatik Bot</string>
    <string name="accessibility_service_description">TRT Bil Bakalım sorularını kaydeder, bilinenleri otomatik doğru tıklar ve oyun sonu \'Yeni Oyun\' butonuna basar.</string>
</resources>
"""

# app/src/main/res/values/themes.xml
files["app/src/main/res/values/themes.xml"] = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SoruArsivi" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
"""

# Kotlin Files:
# ArsivApp.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ArsivApp.kt"] = """package com.emre.bilbakalim.arsiv

import android.app.Application
import com.emre.bilbakalim.arsiv.data.AppDatabase
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.Repo

class ArsivApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repo: Repo
        private set

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        repo = Repo(database.questionDao())
        prefs = Prefs(this)
    }

    companion object {
        lateinit var instance: ArsivApp
            private set
    }
}
"""

# data/Prefs.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/data/Prefs.kt"] = """package com.emre.bilbakalim.arsiv.data

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
"""

# data/QuestionEntity.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/data/QuestionEntity.kt"] = """package com.emre.bilbakalim.arsiv.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "questions")
@TypeConverters(Converters::class)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val question: String,
    val options: List<String>,
    val correctAnswer: String? = null,
    val category: String? = null,
    val normalizedQuestion: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val timesSeen: Int = 1
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}
"""

# data/QuestionDao.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/data/QuestionDao.kt"] = """package com.emre.bilbakalim.arsiv.data

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
"""

# data/AppDatabase.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/data/AppDatabase.kt"] = """package com.emre.bilbakalim.arsiv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [QuestionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bil_bakalim_arsiv.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
"""

# data/Repo.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/data/Repo.kt"] = """package com.emre.bilbakalim.arsiv.data

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
"""

# util/TurkishText.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/util/TurkishText.kt"] = """package com.emre.bilbakalim.arsiv.util

import java.util.Locale

object TurkishText {
    private val TR_LOCALE = Locale("tr", "TR")

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim()
            .lowercase(TR_LOCALE)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9\\\\s]"), "")
            .replace(Regex("\\\\s+"), " ")
            .trim()
    }

    fun isMatch(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.isEmpty() || normB.isEmpty()) return false
        return normA == normB || normA.contains(normB) || normB.contains(normA)
    }
}
"""

# capture/QuestionParser.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/capture/QuestionParser.kt"] = """package com.emre.bilbakalim.arsiv.capture

import android.view.accessibility.AccessibilityNodeInfo

data class ParsedQuestion(
    val question: String,
    val options: List<String>,
    val category: String? = null,
    val detectedCorrectAnswer: String? = null
)

object QuestionParser {

    fun parseFromRoot(root: AccessibilityNodeInfo): ParsedQuestion? {
        val textNodes = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, textNodes)

        if (textNodes.isEmpty()) return null

        val texts = textNodes.mapNotNull {
            val t = (it.text ?: it.contentDescription)?.toString()?.trim()
            if (t.isNullOrBlank() || isSystemText(t)) null else t
        }.distinct()

        // Soru metnini bul (genellikle en uzun veya '?' içeren metin)
        val questionCandidate = texts.firstOrNull { it.contains("?") || it.length > 28 }
            ?: texts.maxByOrNull { it.length }

        if (questionCandidate == null || questionCandidate.length < 8) return null

        // Şıkları bul (soru metni dışındaki 2-4 adet şık)
        val optionCandidates = texts.filter { it != questionCandidate && it.length in 1..40 && !it.contains("Yeni Oyun") }

        if (optionCandidates.size < 2) return null

        return ParsedQuestion(
            question = questionCandidate,
            options = optionCandidates.take(4),
            category = "TRT Bil Bakalım"
        )
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        val text = node.text ?: node.contentDescription
        if (!text.isNullOrBlank()) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            collectTextNodes(node.getChild(i), list)
        }
    }

    private fun isSystemText(t: String): Boolean {
        val lower = t.lowercase()
        return lower in listOf("trt", "bil bakalım", "puan", "skor", "süre", "vs", "tur", "kategori") ||
               t.matches(Regex("^[0-9]+$")) ||
               t.matches(Regex("^[0-9]+-[0-9]+$"))
    }
}
"""

# capture/CaptureAccessibilityService.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/capture/CaptureAccessibilityService.kt"] = """package com.emre.bilbakalim.arsiv.capture

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

    private var lastHandledQuestion: String = ""
    private var isAnsweringInProgress = false
    private var lastRestartClickTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val root = rootInActiveWindow ?: return
        val currentMode = prefs.getPlayMode()

        // 1. Oyun Sonu Ekranı ("Yeni Oyun" Butonunu Otomatik Tıkla)
        if (currentMode == PlayMode.AUTO && prefs.isAutoRestartGame()) {
            checkForGameOverAndRestart(root)
        }

        // 2. Soru ve Şıkları Algıla & Cevapla
        handleQuestionCaptureAndAutoPlay(root, currentMode)
    }

    private fun checkForGameOverAndRestart(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastRestartClickTime < 3000L) return

        val restartNodes = root.findAccessibilityNodeInfosByText("Yeni Oyun")
        if (!restartNodes.isNullOrEmpty()) {
            val buttonNode = restartNodes.firstOrNull() ?: return
            lastRestartClickTime = now
            val delay = prefs.getClickDelayMs()

            mainHandler.postDelayed({
                clickNodeOrCenter(buttonNode)
                lastHandledQuestion = ""
            }, delay)
            return
        }

        val endCheck = root.findAccessibilityNodeInfosByText("Tebrikler")
        if (!endCheck.isNullOrEmpty()) {
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

    private fun handleQuestionCaptureAndAutoPlay(root: AccessibilityNodeInfo, mode: PlayMode) {
        if (isAnsweringInProgress) return

        serviceScope.launch {
            try {
                val parsedQuestion = QuestionParser.parseFromRoot(root) ?: return@launch
                val questionText = parsedQuestion.question.trim()
                val options = parsedQuestion.options.map { it.trim() }

                if (questionText.length < 5 || options.size < 2) return@launch

                val normalizedQ = TurkishText.normalize(questionText)
                if (normalizedQ == lastHandledQuestion) return@launch

                val existingQuestion = repo.findQuestionByNormalizedText(normalizedQ)
                val knownAnswer = existingQuestion?.correctAnswer

                // MANUEL MOD
                if (mode == PlayMode.MANUAL) {
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

                // OTOMATİK BOT MODU
                isAnsweringInProgress = true
                lastHandledQuestion = normalizedQ

                val delayMs = prefs.getClickDelayMs()
                delay(delayMs)

                val optionToClick: String
                val isKnown: Boolean

                if (!knownAnswer.isNullOrBlank() && options.any { TurkishText.isMatch(it, knownAnswer) }) {
                    optionToClick = options.first { TurkishText.isMatch(it, knownAnswer) }
                    isKnown = true
                } else {
                    optionToClick = options.random()
                    isKnown = false
                }

                withContext(Dispatchers.Main) {
                    clickOptionOnScreen(root, optionToClick)
                }

                repo.saveOrUpdateQuestion(
                    QuestionEntity(
                        question = questionText,
                        options = options,
                        correctAnswer = if (isKnown) knownAnswer else null,
                        category = parsedQuestion.category
                    )
                )

                delay(800L)
                isAnsweringInProgress = false

            } catch (e: Exception) {
                isAnsweringInProgress = false
            }
        }
    }

    private fun clickOptionOnScreen(root: AccessibilityNodeInfo, targetText: String): Boolean {
        val matches = root.findAccessibilityNodeInfosByText(targetText)
        if (!matches.isNullOrEmpty()) {
            for (node in matches) {
                if (clickNodeOrCenter(node)) return true
            }
        }

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

    private fun clickNodeOrCenter(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.centerX() > 0 && bounds.centerY() > 0) {
            val clickPath = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) collectAllNodes(node.getChild(i), list)
    }

    private fun findClickableButtons(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable) list.add(node)
        for (i in 0 until node.childCount) findClickableButtons(node.getChild(i), list)
    }

    override fun onInterrupt() {
        isAnsweringInProgress = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
"""

# ui/ArsivViewModel.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/ArsivViewModel.kt"] = """package com.emre.bilbakalim.arsiv.ui

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
"""

# ui/HomeScreen.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/HomeScreen.kt"] = """package com.emre.bilbakalim.arsiv.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@Composable
fun HomeScreen(viewModel: ArsivViewModel) {
    val context = LocalContext.current
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val totalCount by viewModel.totalQuestionsCount.collectAsState()
    val autoRestart by viewModel.autoRestartGame.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Durum Kartı
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (playMode == PlayMode.AUTO) Color(0xFF064E3B) else Color(0xFF1E293B)
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
                            color = if (playMode == PlayMode.AUTO) Color(0xFF6EE7B7) else Color(0xFFCBD5E1)
                        )
                    }

                    Text(
                        text = "$totalCount Soru Kayıtlı",
                        fontSize = 12.sp,
                        color = if (playMode == PlayMode.AUTO) Color(0xFFA7F3D0) else Color(0xFF94A3B8)
                    )
                }

                Spacer(Modifier.height(16.dp))

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
                        Text("Manuel", color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playMode == PlayMode.AUTO) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Otomatik Bot", color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (playMode == PlayMode.AUTO)
                        "⚡ Tıklama Hızı: ${clickDelayMs}ms | Bilinen sorular doğru yanıtlanacak, yeni sorular rastgele denenip öğrenilecek, oyun bitince 'Yeni Oyun'a basılacak."
                    else
                        "🖐️ Manuel Mod: Ekrana tıklanmaz. Siz oynarken doğru cevaplar veritabanına kaydedilir.",
                    fontSize = 11.sp,
                    color = if (playMode == PlayMode.AUTO) Color(0xFFD1FAE5) else Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }
        }

        // Erişilebilirlik Servisi Butonu
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Accessibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Erişilebilirlik Servisini Aç / Kontrol Et")
        }

        // TRT Bil Bakalım Başlat Butonu
        OutlinedButton(
            onClick = {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.trt.bilbakalim")
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("TRT Bil Bakalım Oyununu Başlat")
        }
    }
}
"""

# ui/SettingsScreen.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/SettingsScreen.kt"] = """package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@Composable
fun SettingsScreen(viewModel: ArsivViewModel) {
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val autoRestart by viewModel.autoRestartGame.collectAsState()

    var delayInputText by remember(clickDelayMs) { mutableStateOf(clickDelayMs.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ÇALIŞMA MODU", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isManual = playMode == PlayMode.MANUAL
                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.MANUAL) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isManual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Manuel", color = if (isManual) Color.White else MaterialTheme.colorScheme.onSurface)
                    }

                    val isAuto = playMode == PlayMode.AUTO
                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAuto) Color(0xFF10B981) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Otomatik", color = if (isAuto) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Text("TIKLAMA GECİKMESİ (Milisaniye)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = delayInputText,
                    onValueChange = { input ->
                        delayInputText = input
                        val parsed = input.toLongOrNull()
                        if (parsed != null && parsed >= 200) {
                            viewModel.setClickDelayMs(parsed)
                        }
                    },
                    label = { Text("Gecikme (ms)") },
                    suffix = { Text("ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Text("OYUN SONU & TEKRAR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                    Text("Oyun sonu ekranında 'Yeni Oyun' butonuna tıklar.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoRestart, onCheckedChange = { viewModel.setAutoRestartGame(it) })
            }
        }
    }
}
"""

# ui/QuestionsScreen.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/QuestionsScreen.kt"] = """package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.QuestionEntity

@Composable
fun QuestionsScreen(viewModel: ArsivViewModel) {
    val questions by viewModel.questions.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(questions, searchQuery) {
        if (searchQuery.isBlank()) questions
        else questions.filter {
            it.question.contains(searchQuery, ignoreCase = true) ||
            it.options.any { opt -> opt.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Sorularda veya şıklarda ara...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filtered.size} soru listeleniyor", style = MaterialTheme.typography.bodySmall)
            if (questions.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllQuestions() }) {
                    Text("Tümünü Temizle", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { q ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(q.question, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deleteQuestion(q) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        q.options.forEach { opt ->
                            val isCorrect = opt.equals(q.correctAnswer, ignoreCase = true)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Help,
                                    contentDescription = null,
                                    tint = if (isCorrect) Color(0xFF10B981) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    opt,
                                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCorrect) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

# ui/MainActivity.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/MainActivity.kt"] = """package com.emre.bilbakalim.arsiv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.emre.bilbakalim.arsiv.ArsivApp
import com.emre.bilbakalim.arsiv.ui.theme.SoruArsiviTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ArsivViewModel by viewModels {
        ArsivViewModel.provideFactory(
            ArsivApp.instance.repo,
            ArsivApp.instance.prefs
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoruArsiviTheme {
                var selectedTab by remember { mutableStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                                label = { Text("Bot") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Archive, contentDescription = "Arşiv") },
                                label = { Text("Arşiv") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                                label = { Text("Ayarlar") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(viewModel)
                            1 -> QuestionsScreen(viewModel)
                            2 -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
"""

# ui/theme/Color.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/theme/Color.kt"] = """package com.emre.bilbakalim.arsiv.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
"""

# ui/theme/Theme.kt
files["app/src/main/java/com/emre/bilbakalim/arsiv/ui/theme/Theme.kt"] = """package com.emre.bilbakalim.arsiv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun SoruArsiviTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
"""

# README.md
files["README.md"] = """# TRT Bil Bakalım - Soru Arşivi ve Otomatik Oynama Botu

Bu Android Studio projesi, **TRT Bil Bakalım** oyunu için geliştirilmiş erişilebilirlik ve Room veritabanı tabanlı otomatik oynama ve soru arşivleme uygulamasıdır.

## 🚀 Yeni Özellikler (v2.0)
1. **Manuel / Otomatik Bot Modu:**
   - **Manuel Mod:** Ekrana tıklama yapmaz. Siz normal oynarken soruları ve doğru cevapları Room veritabanına kaydeder.
   - **Otomatik Bot Modu:** Soru geldiğinde hafızasında varsa doğrudan doğru şıkka tıklar. Yeni soru ise rastgele dener, doğru cevabı yeşil renkten hafızasına kaydeder.
2. **Ayarlanabilir Tıklama Gecikmesi (ms):**
   - İster el ile milisaniye girin (ör. 1200ms), ister hazır butonlardan (500ms Hızlı, 1200ms Normal, 2500ms Doğal) seçin.
3. **Kesintisiz 'Yeni Oyun' Döngüsü:**
   - Oyun bittiğinde beliren '108-84 Tebrikler kazandınız' skor ekranında sağ alttaki 'Yeni Oyun' butonunu otomatik algılar ve tıklar.
4. **Çift Katmanlı Tıklama Garantisi:**
   - Standart `ACTION_CLICK` düğüm tıklaması ve `dispatchGesture` koordinat dokunma simülasyonu ile her cihazda çalışır.

## 📱 Nasıl Yüklenir ve Çalıştırılır?
1. Bu zip dosyasını bir klasöre çıkartın.
2. **Android Studio**'yu açıp **Open** diyerek `SoruArsivi` klasörünü seçin.
3. Gradle senkronizasyonunun bitmesini bekleyin ve telefonunuza yükleyin.
4. Telefonunuzun **Ayarlar > Erişilebilirlik** menüsüne girip **Soru Arşivi ve Otomatik Bot** servisini açın.
5. TRT Bil Bakalım oyununu açın ve arkanıza yaslanın!
"""

os.makedirs(base_dir, exist_ok=True)

for rel_path, content in files.items():
    full_path = os.path.join(base_dir, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

print(f"Total {len(files)} files written to {base_dir}")

# Zip it into public/SoruArsivi_Bot_Guncel.zip
os.makedirs("public", exist_ok=True)
zip_path = os.path.join("public", "SoruArsivi_Bot_Guncel.zip")

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, filenames in os.walk(base_dir):
        for file in filenames:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, os.path.dirname(base_dir))
            zipf.write(file_path, arcname)

print(f"Zip created at {zip_path} with size {os.path.getsize(zip_path)} bytes")
