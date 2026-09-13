package com.emre.bilbakalim.arsiv.util

import java.security.MessageDigest
import java.util.Locale

/**
 * Türkçe metin normalleştirme yardımcıları.
 *
 * Aynı sorunun iki kez kaydedilmemesi için metni "parmak izine" çeviririz:
 * büyük/küçük harf, noktalama, boşluk ve Türkçe aksan farkları silinir.
 * Böylece OCR "Felsefe'nin" derken erişilebilirlik "Felsefenin" dese bile
 * ikisi aynı kayda düşer.
 */
object TurkishText {

    private val TR: Locale = Locale.forLanguageTag("tr-TR")

    /** Türkçe kurallarına göre küçük harfe çevirir (I -> ı, İ -> i). */
    fun lower(s: String): String = s.lowercase(TR)

    /** Türkçe kurallarına göre büyük harfe çevirir. */
    fun upper(s: String): String = s.uppercase(TR)

    /** Aksanlı ve Türkçe'ye özgü harfleri ASCII karşılıklarına indirger. */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    'ı', 'î', 'í', 'ì' -> 'i'
                    'ğ' -> 'g'
                    'ü', 'û', 'ú', 'ù' -> 'u'
                    'ş' -> 's'
                    'ö', 'ô', 'ó', 'ò' -> 'o'
                    'ç' -> 'c'
                    'â', 'á', 'à', 'ä' -> 'a'
                    'ê', 'é', 'è', 'ë' -> 'e'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /**
     * OCR çıktısındaki tipik gürültüyü temizler:
     * kırık boşluklar, tekrar eden noktalama, baştaki/sondaki çöp karakterler.
     */
    fun cleanOcr(raw: String): String {
        var s = raw
            .replace(' ', ' ')
            .replace(Regex("[|¦]"), "I")
            .replace(Regex("[“”„‟]"), "\"")
            .replace(Regex("[‘’‚‛]"), "'")
            .replace(Regex("\\s*\\n\\s*"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        // Baştaki/sondaki anlamsız tek karakterler ve süslemeler
        s = s.trim(' ', '\t', '·', '•', '*', '_', '-', '—', '–', '~', '"', '\'')

        // "?" ve "." tekrarlarını sadeleştir
        s = s.replace(Regex("\\?{2,}"), "?").replace(Regex("\\.{4,}"), "...")
        return s.trim()
    }

    /** Karşılaştırma için sadeleştirilmiş anahtar: sadece harf ve rakam. */
    fun normalizeKey(s: String): String =
        fold(lower(s)).replace(Regex("[^a-z0-9]"), "")

    /** Soru metninden kararlı bir parmak izi üretir. */
    fun fingerprint(question: String, options: List<String>): String {
        val key = buildString {
            append(normalizeKey(question))
            // Şıkların sırası oyunda değişebiliyor; bu yüzden sıralayıp ekliyoruz.
            options.map { normalizeKey(it) }.filter { it.isNotBlank() }.sorted()
                .forEach { append('|').append(it) }
        }
        return sha256(key)
    }

    fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append("0123456789abcdef"[v ushr 4])
            hex.append("0123456789abcdef"[v and 0x0F])
        }
        return hex.toString()
    }

    /**
     * 0.0 - 1.0 arası benzerlik. OCR bir iki harfi yanlış okuduğunda
     * parmak izi tutmaz; bu yüzden ikinci bir güvenlik ağı olarak kullanılır.
     */
    fun similarity(a: String, b: String): Float {
        val x = normalizeKey(a)
        val y = normalizeKey(b)
        if (x.isEmpty() && y.isEmpty()) return 1f
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        // Uzunluk farkı çok büyükse hesaplamaya girme
        val maxLen = maxOf(x.length, y.length)
        if (minOf(x.length, y.length).toFloat() / maxLen < 0.6f) return 0f
        val dist = levenshtein(x, y)
        return 1f - dist.toFloat() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private val LEADING_NUMBER = Regex("^\\d{1,3}\\s*[.)\\-]\\s*")
    private val LEADING_CHROME = Regex(
        "^(süre\\s*bitti|sure\\s*bitti|süre\\s*doldu|zaman\\s*doldu|muhteşem|muhtesem|" +
            "biraz\\s*daha\\s*gayret|tebrikler|harika|bravo|doğru\\s*cevap|kombo|combo|" +
            "seri|süper|super|mükemmel|mukemmel|aferin|çok\\s*yaklaştın|cok\\s*yaklastin)" +
            "\\s*[!.,:;x×]*\\s*\\d*\\s*"
    )

    /**
     * Doğru cevaptan sonra ekrana düşen puan balonu: "+5", "+10", "-5".
     *
     * Metnin iki ucunda da aranıyor çünkü OCR bunu soruyla **aynı blokta**
     * döndürebiliyor: arşivde "…gezegen hangisidir? +5" diye kaydedilmiş
     * sorular bundan. Parça bazlı süzgeç bunu hiç göremiyor, metnin
     * kendisinden sökmek gerekiyor.
     *
     * Sondaki kural işaretten önceki karaktere bakıyor: rakamsa dokunmuyor.
     * Yoksa "Sonuç kaçtır: 2 + 2" sorusunun sonundaki toplama da puan
     * balonu sanılıp kesiliyordu.
     */
    private val SCORE_BADGE_END =
        Regex("(?<=[^0-9\\s])[\\s(]*[+\\-±]\\s*\\d{1,4}\\s*[)!.,:;]*\\s*\$")
    private val SCORE_BADGE_START = Regex("^\\s*[+\\-±]\\s*\\d{1,4}\\s*[!.,:;]*\\s*")

    /**
     * Soru metnine yapışmış arayüz parçalarını iki uçtan da söker.
     *
     * Soru numarası ("17."), "Süre Bitti" / "KOMBO" gibi banner'lar ve doğru
     * cevaptan sonra düşen "+5" puan balonu soru kartının üstünde ya da
     * üzerinde duruyor; OCR bunları soruyla **aynı blokta** döndürebiliyor,
     * yani parça bazlı süzgeçler göremiyor. Temizlenmezse aynı soru bir kez
     * temiz bir kez bu fazlalıkla okunup iki ayrı kayıt oluyor — arşivde
     * "KOMBO Türkiye'nin…" ve "…hangisidir? +5" diye duran kayıtlar bundan.
     */
    fun stripQuestionChrome(s: String): String {
        var t = s.trim()
        var guard = 0
        while (guard++ < 8) {
            val before = t
            val low = lower(t)

            var cut = 0
            LEADING_NUMBER.find(low)?.let { if (it.range.first == 0) cut = it.range.last + 1 }
            if (cut == 0) LEADING_CHROME.find(low)?.let { if (it.range.first == 0) cut = it.range.last + 1 }
            if (cut == 0) SCORE_BADGE_START.find(low)?.let { if (it.range.first == 0) cut = it.range.last + 1 }
            if (cut > 0) t = t.substring(cut).trim()

            // Sondaki puan balonu. Soru işaretinden sonra geldiği için
            // metnin anlamını bozmadan kesilebiliyor.
            t = SCORE_BADGE_END.replace(t, "").trim()

            if (t == before) break
        }
        return t
    }

    /**
     * Sorunun olumsuzluk parmak izi.
     *
     * "Hangisi ... özelliklerindendir?" ile "... özelliklerinden değildir?"
     * arasındaki karakter benzerliği %91'e çıkıyor ve şıkları da aynı oluyor —
     * ama bunlar zıt sorular. Bu imza farklıysa iki kayıt asla birleştirilmez.
     */
    fun negationSignature(s: String): Int {
        val low = lower(s)
        var sig = 0
        if (low.contains("değil")) sig = sig or 1
        if (low.contains("yanlış")) sig = sig or 2
        if (low.contains("olmayan")) sig = sig or 4
        if (low.contains("hariç")) sig = sig or 8
        if (low.contains("dışında")) sig = sig or 16
        if (low.contains("söylenemez")) sig = sig or 32
        return sig
    }

    /** Metinde arayüz uyarısı geçiyor mu — iki kayıttan temiz olanı seçmek için. */
    fun hasChromePhrase(s: String): Boolean {
        val low = lower(s)
        return listOf("süre bitti", "sure bitti", "süre doldu", "muhteşem", "tebrikler",
            "biraz daha gayret").any { low.contains(it) }
    }

    /**
     * Bir şık metninin listedeki sırasını bulur.
     *
     * Şıkların sırası her turda değişiyor. Bu yüzden "doğru cevap 2. şık"
     * bilgisi tek başına işe yaramaz; hangi *metnin* doğru olduğunu bilip
     * onu o anki listede aramak gerekir. Kayıttaki metinle ekrandaki metin
     * arasında OCR kaynaklı bir iki harf farkı olabileceği için, birebir
     * eşleşme bulunamazsa en yakın şık kabul edilir.
     *
     * Hiçbir şık yeterince benzemiyorsa null döner — yanlış şıkka basmaktansa
     * bilmediğimizi söylemek yeğdir.
     */
    fun matchIndex(options: List<String>, text: String?): Int? {
        if (text.isNullOrBlank() || options.isEmpty()) return null
        val key = normalizeKey(text)
        if (key.isEmpty()) return null

        options.forEachIndexed { i, o -> if (normalizeKey(o) == key) return i }

        var best = -1
        var bestSim = 0f
        options.forEachIndexed { i, o ->
            val sim = similarity(o, text)
            if (sim > bestSim) {
                bestSim = sim
                best = i
            }
        }
        return if (best >= 0 && bestSim >= OPTION_MATCH_MIN) best else null
    }

    /** Şık eşleşmesi için en düşük benzerlik. */
    private const val OPTION_MATCH_MIN = 0.85f

    /** "A) Platon", "1. Platon", "- Platon" gibi baştaki şık işaretlerini atar. */
    fun stripOptionPrefix(s: String): String =
        s.replace(Regex("^\\s*[(\\[]?\\s*([A-Da-dEeĞğ]|[1-5])\\s*[).\\]:\\-–]\\s+"), "").trim()

    /**
     * Metnin gerçekten bir soru cümlesi olup olmadığına karar verir.
     *
     * Yarışma soruları neredeyse her zaman "?" içerir; içermeyenler de
     * belirli kalıplarla kurulur. Bu kontrol, lobi/skor ekranlarındaki
     * "Felsefe Bilme Oranı" gibi başlıkların soru sanılıp kaydedilmesini
     * engellemek için kullanılır.
     */
    fun looksLikeQuestion(s: String): Boolean {
        val t = s.trim()
        if (t.length < 12) return false
        if (t.contains('?')) return true
        val lower = lower(t)
        val cues = listOf(
            "hangi", "hangisi", "hangisidir", "kimdir", "kim tarafından",
            "nedir", "ne zaman", "nerede", "kaç", "neden", "niçin",
            "aşağıdaki", "yukarıdaki", "nasıl", "doğrudur", "yanlıştır",
            "adı nedir", "adı verilir", "adlandırılır", "denir", "bilinir",
            "değildir", "söylenir", "bulunur", "kullanılır"
        )
        return cues.any { lower.contains(it) }
    }
}
