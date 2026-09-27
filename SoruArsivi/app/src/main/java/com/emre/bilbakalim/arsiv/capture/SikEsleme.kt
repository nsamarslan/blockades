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
     *
     * false yalnızca iki tarafın imzası karşılaştırılıp tutmadığında (ya da
     * ekranın imzası eksik olduğunda) döner. Kayıt imzasızsa (3.9 öncesi)
     * doğrulanacak bir şey yok: eskiden olduğu gibi metinle devam edilir ve
     * eksik imzalar bu karşılaşmada yazılır. Yoksa o kayıtlar bir daha hiç
     * eşleşmiyor, bot da o sorulara hiç basmıyordu.
     */
    fun okunamayanlarAyni(
        stored: List<String>, storedSigs: List<String?>,
        screen: List<String>, screenSigs: List<String?>
    ): Boolean {
        val a = stored.indices.filter { stored[it] == TurkishText.UNREADABLE_OPTION }
        val b = screen.indices.filter { screen[it] == TurkishText.UNREADABLE_OPTION }
        if (a.isEmpty() && b.isEmpty()) return true
        if (storedSigs.size != stored.size || storedSigs.any { it == null }) return true
        if (screenSigs.size != screen.size) return false
        if (a.size == b.size) {
            val matched = a.map { SikImzasi.enYakin(storedSigs[it], b, screenSigs) }
            if (matched.all { it != null } && matched.toSet().size == b.size) return true
        }
        // OCR bu kez başka bir şıkkı okuyamamış ya da okunamayanı «V» diye
        // okumuş olabilir. Dört şık görüntüce birebir eşleşiyorsa aynı settir.
        return SikImzasi.eslesmeSirasi(storedSigs, screenSigs) != null
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
            if (!shortOrSymbol || screenSigs.isEmpty()) return textIndex
            // İki tarafta da imza varsa kısa/simgeli şıkkı metinle tek
            // eşleşti diye onaylama: resimde başka karakter olabilir.
            if (storedSig != null && screenSigs.getOrNull(textIndex) != null) {
                return if (SikImzasi.enYakin(storedSig, listOf(textIndex), screenSigs) == textIndex)
                    textIndex else null
            }
            // İmzalardan biri yok, görüntüyle doğrulanamıyor. OCR'ın ayrı
            // simgeleri aynı okuduğu yer tek harf ya da rakam («V», «8»):
            // orada tahmin yok. "12", "1/6", "-4", "<" gibi metinlerde metin
            // kararı geçerli (işaretler zaten birebir tutmak zorunda); yoksa
            // imzası alınamamış kayıtlarda bu cevapların hiçbiri bulunmuyordu.
            return if (tekHarfYaDaRakam(text)) null else textIndex
        }
        return sigIndex
    }

    private fun tekHarfYaDaRakam(text: String): Boolean {
        val t = text.trim()
        return t.length == 1 && t[0].isLetterOrDigit()
    }
}
