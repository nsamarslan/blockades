package com.emre.bilbakalim.arsiv.util

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
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
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
