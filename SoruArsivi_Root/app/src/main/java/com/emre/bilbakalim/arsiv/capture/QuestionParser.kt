package com.emre.bilbakalim.arsiv.capture

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
