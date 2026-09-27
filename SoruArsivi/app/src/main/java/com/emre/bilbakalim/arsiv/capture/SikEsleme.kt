package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.util.TurkishText

/**
 * Arşivdeki şıkkı, yerleri karıştırılmış ekrandaki şıklarla güvenle eşler.
 * Metin okunamıyorsa yalnızca imzaya güvenir; tek karakterlik/simgeli
 * OCR sonuçlarını da imza varsa onunla doğrular. Belirsizlikte null döner.
 */
object SikEsleme {
    /**
     * Aynı sorunun OCR'siz şıklarının *kümesi* değiştiyse, parmak izi
     * metinle aynı görünse de başka bir cevap seti olabilir. Birbirine
     * benzeyen ama yerleri değişmiş şıkları birebir görsel eşleyerek doğrula.
     */
    fun okunamayanlarAyni(
        stored: List<String>, storedSigs: List<String?>,
        screen: List<String>, screenSigs: List<String?>
    ): Boolean {
        val a = stored.indices.filter { stored[it] == TurkishText.UNREADABLE_OPTION }
        val b = screen.indices.filter { screen[it] == TurkishText.UNREADABLE_OPTION }
        if (a.isEmpty() && b.isEmpty()) return true
        if (a.size != b.size || storedSigs.size != stored.size || screenSigs.size != screen.size)
            return false
        val matched = a.map { SikImzasi.enYakin(storedSigs[it], b, screenSigs) }
        return matched.all { it != null } && matched.toSet().size == b.size
    }

    fun bul(
        text: String?, storedSig: String?,
        screenOptions: List<String>, screenSigs: List<String?>
    ): Int? {
        if (text.isNullOrBlank()) return null
        val unreadable = text == TurkishText.UNREADABLE_OPTION
        val textIndex = if (unreadable) null else TurkishText.matchIndex(screenOptions, text)
        val candidates = TurkishText.matchCandidates(screenOptions, text)
        val sigIndex = SikImzasi.enYakin(storedSig, candidates, screenSigs)
        if (unreadable) return sigIndex

        // OCR'nin «V» dediği şey V, ∨, ∧ veya > olabilir; benzer şekilde
        // «8» bazen ‰ olabilir. Metin tek başına ayırt edici sayılmaz.
        val shortOrSymbol = text.length <= 2 ||
            TurkishText.optionKey(text) != TurkishText.normalizeKey(text)
        if (textIndex != null) {
            // İki tarafta da imza varsa kısa/simgeli şıkkı metinle tek
            // eşleşti diye onaylama: resimde başka karakter olabilir.
            if (shortOrSymbol && screenSigs.isNotEmpty()) {
                // Kutu OCR yolundayız ama imzalardan biri eksikse, kısa
                // metnin tek karşılığına bakıp tahmin etmek güvenli değil.
                if (storedSig == null || screenSigs.getOrNull(textIndex) == null) return null
                return if (SikImzasi.enYakin(storedSig, listOf(textIndex), screenSigs) == textIndex)
                    textIndex else null
            }
            return textIndex
        }
        return sigIndex
    }
}
