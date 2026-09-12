package com.emre.bilbakalim.arsiv.capture

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
