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
        // OCR bu kez başka bir şıkkı okuyamamış ya da okunamayanı «V» diye
        // okumuş olabilir: okunabilenler metinden eşleşiyor, geriye kalanlar
        // karşılıklı olmalı.
        val e = metinleEsle(stored, screen)
        if (e.kalanKayit.size != e.kalanEkran.size) return false
        // ML Kit tek başına duran "0"ı çoğu kez okuyamıyor ve rakamların
        // imzası kareden kareye kararsız. Geriye tek şık kaldıysa ve okunan
        // tarafı bir sayıysa aynı şıktır; imza yalnızca simgeleri ayırıyor.
        if (e.kalanKayit.size == 1 &&
            (stored[e.kalanKayit[0]].any { it.isDigit() } || screen[e.kalanEkran[0]].any { it.isDigit() })
        ) return true
        return imzaylaEsle(e, storedSigs, screenSigs) != null
    }

    /**
     * Kayıttaki [dogru]. şık ekranda kaçıncı sırada? Önce metin (gerekirse
     * imzayla doğrulanarak), metin hiç tutmuyorsa öteki şıklardan eleme.
     * Bilinen cevaba basan bot da dışa aktarımdaki "doğru şık" işareti de
     * bunu kullanıyor. Karar verilemezse null.
     */
    fun ekrandaBul(
        kayit: List<String>, kayitImza: List<String?>, dogru: Int,
        ekran: List<String>, ekranImza: List<String?>
    ): Int? {
        val text = kayit.getOrNull(dogru) ?: return null
        return bul(text, kayitImza.getOrNull(dogru), ekran, ekranImza)
            ?: if (TurkishText.matchCandidates(ekran, text).isEmpty()) {
                siraBul(kayit, kayitImza, ekran, ekranImza)?.getOrNull(dogru)
            } else null
    }

    /**
     * Kayıttaki her şıkkın ekrandaki sırası, metin doğrudan tutmadığında
     * (kayıttaki doğru şık «(okunamadı)», ekranda bu kez okunmuş ya da tersi).
     * Okunabilen şıklar metinden eşleşiyor. Geriye tek şık kaldıysa ve bir
     * tarafı okunamayansa eleme yetiyor; kalanlar birden çoksa her biri
     * imzayla açık farkla eşleşmeli. Karar verilemezse null.
     *
     * Eskiden dört şıkkın dördü de imzayla eşlenmek zorundaydı; rakam
     * imzaları buna dayanmıyor.
     */
    fun siraBul(
        stored: List<String>, storedSigs: List<String?>,
        screen: List<String>, screenSigs: List<String?>
    ): List<Int>? {
        if (stored.size != screen.size) return null
        val e = metinleEsle(stored, screen)
        if (e.kalanKayit.size != e.kalanEkran.size) return null
        if (e.kalanKayit.size == 1) {
            val s = e.kalanKayit[0]
            val k = e.kalanEkran[0]
            if (stored[s] == TurkishText.UNREADABLE_OPTION || screen[k] == TurkishText.UNREADABLE_OPTION) {
                e.sira[s] = k
                return stored.indices.map { e.sira.getValue(it) }
            }
        }
        val kalan = imzaylaEsle(e, storedSigs, screenSigs) ?: return null
        e.sira.putAll(kalan)
        return stored.indices.map { e.sira[it] ?: return null }
    }

    private class MetinEslesmesi(
        /** Kayıttaki sıra → ekrandaki sıra, metinden eşleşenler. */
        val sira: MutableMap<Int, Int>,
        val kalanKayit: List<Int>,
        val kalanEkran: List<Int>
    )

    private fun metinleEsle(stored: List<String>, screen: List<String>): MetinEslesmesi {
        val sira = mutableMapOf<Int, Int>()
        val kalanKayit = mutableListOf<Int>()
        for (i in stored.indices) {
            val j = if (stored[i] == TurkishText.UNREADABLE_OPTION) null
            else TurkishText.matchIndex(screen, stored[i])?.takeIf { it !in sira.values }
            if (j == null) kalanKayit += i else sira[i] = j
        }
        return MetinEslesmesi(sira, kalanKayit, screen.indices.filter { it !in sira.values })
    }

    /** Kalan şıkların her biri kalan ekran şıklarından birine imzayla, benzersiz. */
    private fun imzaylaEsle(
        e: MetinEslesmesi, storedSigs: List<String?>, screenSigs: List<String?>
    ): Map<Int, Int>? {
        val esler = e.kalanKayit.associateWith {
            SikImzasi.enYakin(storedSigs.getOrNull(it), e.kalanEkran, screenSigs) ?: return null
        }
        return esler.takeIf { it.values.toSet().size == e.kalanEkran.size }
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

        // OCR'nin «V» dediği şey V, ∨, ∧ veya > olabilir. Metin tek başına
        // ayırt edici sayılmaz. Rakam içeren metin ("1", "-4", "1/36") bunun
        // dışında: OCR rakamı güvenilir okuyor, rakamların imzası ise ince
        // gövdeleri yüzünden kareden kareye 56 biti aşıyor. Ekrandaki «1»
        // arşivdeki «1»le eşleşmiyor sayılıp rastgele basılıyor, cevap da
        // hiç yazılamıyordu.
        val shortOrSymbol = text.none { it.isDigit() } &&
            (text.length <= 2 || TurkishText.optionKey(text) != TurkishText.normalizeKey(text))
        if (textIndex != null) {
            if (!shortOrSymbol || screenSigs.isEmpty()) return textIndex
            // İki tarafta da imza varsa kısa/simgeli şıkkı metinle tek
            // eşleşti diye onaylama: resimde başka karakter olabilir.
            if (storedSig != null && screenSigs.getOrNull(textIndex) != null) {
                return if (SikImzasi.enYakin(storedSig, listOf(textIndex), screenSigs) == textIndex)
                    textIndex else null
            }
            // İmzalardan biri yok, görüntüyle doğrulanamıyor. OCR'ın ayrı
            // simgeleri aynı okuduğu yer tek harf («V»): orada tahmin yok.
            // "<", "ab" gibi metinlerde metin kararı geçerli (işaretler zaten
            // birebir tutmak zorunda).
            return if (tekHarf(text)) null else textIndex
        }
        return sigIndex
    }

    private fun tekHarf(text: String): Boolean {
        val t = text.trim()
        return t.length == 1 && t[0].isLetter()
    }
}
