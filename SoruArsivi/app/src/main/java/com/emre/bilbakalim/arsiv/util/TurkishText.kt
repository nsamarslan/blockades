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

        // Rakamdan önceki uzun çizgi ve matematik eksisi düz eksi: "−16",
        // "–16" → "-16". Şık eşleştirmesi ancak böyle aynı metni görüyor.
        s = s.replace(EKSI_BENZERI, "-").replace(BASTAKI_EKSI_BOSLUK, "-")

        // Baştaki/sondaki anlamsız tek karakterler ve süslemeler. Rakamdan
        // hemen önceki eksi süs değil, sayının işareti: "-16" eskiden "16"
        // oluyordu ve "-16 / -4 / 4 / 16" şıkları arşive "16 / 4 / 4 / 16"
        // diye yazılıyordu. Şıklar ayırt edilemiyor, doğru cevap hiçbir
        // zaman kaydedilemiyordu.
        s = s.trimEnd(*SUSLER)
        while (s.isNotEmpty() && s[0] in SUSLER && !(s[0] == '-' && s.getOrNull(1)?.isDigit() == true)) {
            s = s.substring(1)
        }

        // "?" ve "." tekrarlarını sadeleştir
        s = s.replace(Regex("\\?{2,}"), "?").replace(Regex("\\.{4,}"), "...")
        return s.trim()
    }

    private val SUSLER = charArrayOf(' ', '\t', '·', '•', '*', '_', '-', '—', '–', '~', '"', '\'')
    private val EKSI_BENZERI = Regex("[−–—](?=\\s*\\d)")
    /** Baştaki "- 16": OCR eksiyle sayının arasına boşluk koyabiliyor. */
    private val BASTAKI_EKSI_BOSLUK = Regex("^-\\s+(?=\\d)")

    /** Karşılaştırma için sadeleştirilmiş anahtar: sadece harf ve rakam. */
    fun normalizeKey(s: String): String = fold(lower(s)).replace(HARF_RAKAM_DISI, "")

    // Düzenli ifadeler bir kez derleniyor. Eskiden her çağrıda yeniden
    // derleniyordu; tekrar denetimi bu iki fonksiyonu arşivin her satırı
    // için çağırdığından, bilinmeyen her soruda binlerce derleme demekti.
    private val HARF_RAKAM_DISI = Regex("[^a-z0-9]")
    private val KELIME_AYRACI = Regex("[^a-z0-9]+")

    /**
     * Metni normalleştirilmiş kelimelere ayırır.
     *
     * normalizeKey boşlukları da sildiği için kelime sınırlarını kaybediyor;
     * "…ölçütlerindendir" ile "…ölçütlerinden değildir" karakter dizisi
     * olarak neredeyse aynı görünüyor. Kelime listesiyle bakıldığında ise
     * son kelimeler açıkça farklı.
     */
    fun words(s: String): List<String> =
        fold(lower(s)).split(KELIME_AYRACI).filter { it.isNotEmpty() }

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
    fun similarity(a: String, b: String): Float = similarityOfKeys(normalizeKey(a), normalizeKey(b))

    /** [similarity]'nin zaten [normalizeKey]'den geçmiş anahtarlar için olanı. */
    fun similarityOfKeys(x: String, y: String): Float {
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

    private val DIGITS_ONLY = Regex("\\d+")

    /**
     * İki şık metni "aynı şık" sayılır mı?
     *
     * Birebir eşitlik yetmiyor: OCR aynı şıkkın sonundaki harfi düşürebiliyor
     * ("Fransa" yerine "Frans"). Bu yüzden dört harften uzun kelimelerde tek
     * harflik fark hoş görülüyor.
     *
     * Sayı şıklarında ise tek harf farkı gerçek bir fark: "82" ile "92" ayrı
     * cevaplar. Orada birebir eşitlik aranıyor.
     */
    fun sameOptionText(a: String, b: String): Boolean =
        sameOptionKey(normalizeKey(a), normalizeKey(b))

    /** [sameOptionText]'in zaten [normalizeKey]'den geçmiş anahtarlar için olanı. */
    fun sameOptionKey(x: String, y: String): Boolean {
        if (x == y) return x.isNotEmpty()
        if (DIGITS_ONLY.matches(x) || DIGITS_ONLY.matches(y)) return false
        if (minOf(x.length, y.length) < 4) return false
        if (kotlin.math.abs(x.length - y.length) > 1) return false
        return levenshtein(x, y) <= 1
    }

    /**
     * İki şık listesi (sıraları değişmiş olabilir) aynı dörtlü mü?
     *
     * Sıra her turda değiştiği için birebir eşleştirme yapılıyor: soldaki her
     * şıkkın sağda kendine ait bir karşılığı olmalı, aynı karşılık iki kez
     * kullanılamaz.
     */
    fun optionsNearlyMatch(a: List<String>, b: List<String>): Boolean =
        optionKeysNearlyMatch(a.map { normalizeKey(it) }, b.map { normalizeKey(it) })

    /** [optionsNearlyMatch]'in [normalizeKey]'den geçmiş anahtarlar için olanı. */
    fun optionKeysNearlyMatch(a: List<String>, b: List<String>): Boolean {
        if (a.size < 4 || a.size != b.size) return false
        val kalan = b.toMutableList()
        for (o in a) {
            val i = kalan.indexOfFirst { sameOptionKey(o, it) }
            if (i < 0) return false
            kalan.removeAt(i)
        }
        return true
    }

    /**
     * İki şık listesinin kaç şıkkı birbirini tutuyor? Sıra önemsiz, her şık
     * en fazla bir kez kullanılıyor. Anahtarlar [normalizeKey]'den geçmiş olmalı.
     */
    fun optionKeyOverlap(a: List<String>, b: List<String>): Int {
        val kalan = b.toMutableList()
        var ortak = 0
        for (o in a) {
            val i = kalan.indexOfFirst { sameOptionKey(o, it) }
            if (i < 0) continue
            kalan.removeAt(i)
            ortak++
        }
        return ortak
    }

    /**
     * İki kelime dizisi bir fiilin olumsuzluk ekiyle mi ayrılıyor?
     *
     * "Hilesiz bir zar atıldığında 3 **gelme** olasılığı" ile "3 **gelmeme**
     * olasılığı" %96 benziyor; olumsuzluk imzası ([negationSignature]) ayrı
     * kelimelere bakıyor ve eki göremiyor. İki biçim tanınıyor:
     *  - araya "me"/"ma" girmiş: gel-me / gel-me-me, al-mak / al-ma-mak
     *  - geniş zaman: gel-ir / gel-mez, ol-ur / ol-maz, yapıl-ır / yapıl-maz
     *
     * Kelimeler [words]'ten gelmeli (küçük, katlanmış). Kelime sayıları
     * farklıysa bakılmıyor.
     */
    fun olumsuzlukEkiFarki(wa: List<String>, wb: List<String>): Boolean {
        if (wa.size != wb.size) return false
        for (i in wa.indices) {
            val x = wa[i]
            val y = wb[i]
            if (x == y) continue
            if (olumsuzCifti(x, y) || olumsuzCifti(y, x)) return true
        }
        return false
    }

    private fun olumsuzCifti(olumlu: String, olumsuz: String): Boolean {
        if (olumsuz.length == olumlu.length + 2) {
            // Kökte en az iki harf kalsın: baştaki "ma"/"me" ek değil.
            for (p in 2..olumlu.length) {
                if ((olumsuz.startsWith("me", p) || olumsuz.startsWith("ma", p)) &&
                    olumsuz.regionMatches(0, olumlu, 0, p) &&
                    olumsuz.regionMatches(p + 2, olumlu, p, olumlu.length - p)
                ) return true
            }
        }
        for (ek in GENIS_ZAMAN) {
            if (!olumlu.endsWith(ek)) continue
            val kok = olumlu.dropLast(ek.length)
            if (kok.length >= 2 && (olumsuz == kok + "mez" || olumsuz == kok + "maz")) return true
        }
        return false
    }

    private val GENIS_ZAMAN = listOf("ir", "ur", "er", "ar", "r")

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

        // Birebir eşleşme, sıkıdan gevşeğe dört anahtarla. Bir kademede tek
        // şık tutuyorsa o; birden çok şık tutuyorsa şıklar o kademede ayırt
        // edilemiyor demektir — daha gevşek kademe de ayıramaz: null.
        //
        //  1. Türkçe harfleri ve simgeleri koruyan anahtar. "Töz" ile "Toz",
        //     "Öz" ile "Oz" ancak burada ayrılıyor. Eskiden ilk kademe
        //     harfleri katlıyordu (ö→o) ve İLK eşleşeni döndürüyordu: "Öz /
        //     Toz / Oz / Töz" sorusunda cevap "Töz" iken bot "Toz"a basıyor,
        //     arşive de "Toz" yazılıyordu. Kırmızı görülüp düzeltilmeye
        //     çalışıldığında aynı eşleştirme yine "Toz"u buluyordu.
        //  2. Türkçe harfleri koruyan ama noktalama ve boşluğu atan anahtar
        //     ("Töz." ile "Töz").
        //  3. Harfleri katlanmış, simgeli anahtar. "Satış fiyatı > Maliyet"
        //     ile "Satış fiyatı < Maliyet"i ayırıyor (normalizeKey simgeleri
        //     siliyor).
        //  4. normalizeKey: yalnızca harf-rakam, katlanmış.
        for (anahtar in ESLESME_KADEMELERI) {
            val k = anahtar(text)
            if (k.isEmpty()) continue
            val tutan = options.indices.filter { anahtar(options[it]) == k }
            if (tutan.size == 1) return tutan[0]
            if (tutan.size > 1) return null
        }
        if (normalizeKey(text).isEmpty()) return null

        // 5. Bulanık benzerlik (OCR harf hataları için).
        var best = -1
        var bestSim = 0f
        options.forEachIndexed { i, o ->
            val sim = similarity(o, text)
            if (sim > bestSim) {
                bestSim = sim
                best = i
            }
        }
        if (best < 0 || bestSim < OPTION_MATCH_MIN) return null
        // En iyi adayla aynı anahtarı taşıyan bir başkası varsa belirsiz.
        val bestKey = normalizeKey(options[best])
        return if (options.count { normalizeKey(it) == bestKey } > 1) null else best
    }

    /**
     * Türkçe harfleri koruyan, yalnızca harf-rakamdan oluşan anahtar.
     *
     * [normalizeKey] "ö"yü "o"ya indirdiği için "Töz" ile "Toz"u aynı sayıyor;
     * tekrar denetimi için doğru (OCR noktaları düşürebiliyor), ama iki şık
     * yalnızca bu harflerle ayrılıyorsa hangisine basılacağına bununla
     * karar verilmeli.
     */
    fun distinctKey(s: String): String = lower(s).filter { it.isLetterOrDigit() }

    /** Boşluk ve büyük-küçük dışında her şeyi koruyan yumuşak anahtar. */
    private fun softKey(s: String): String =
        fold(lower(s)).trim().replace(BOSLUKLAR, " ")

    /** [softKey]'in Türkçe harfleri katlamayan hâli. */
    private fun strictKey(s: String): String = lower(s).trim().replace(BOSLUKLAR, " ")

    private val BOSLUKLAR = Regex("\\s+")

    private val ESLESME_KADEMELERI: List<(String) -> String> =
        listOf(::strictKey, ::distinctKey, ::softKey, ::normalizeKey)

    /** Şık eşleşmesi için en düşük benzerlik. */
    private const val OPTION_MATCH_MIN = 0.85f

    /**
     * "A) Platon", "B. Platon", "1) Platon" gibi baştaki şık işaretlerini atar.
     *
     * Rakamdan sonra gelen **nokta** bilerek işaret sayılmıyor: Türkçede
     * sıra sayısı böyle yazılır ("1. Dönem", "2. Mahmut", "3. Selim").
     * Eskiden sayılıyordu ve "1. Dönem / 4. Dönem / 2. Dönem / 3. Dönem"
     * şıkları arşive dört kez "Dönem" diye yazılmıştı: şıklar birbirinden
     * ayırt edilemiyor, doğru cevap hiçbir zaman kaydedilemiyordu. Rakam
     * yalnızca ")" ya da "]" ile kapanıyorsa işarettir.
     */
    fun stripOptionPrefix(s: String): String = s.replace(OPTION_PREFIX, "").trim()

    private val OPTION_PREFIX =
        Regex("^\\s*[(\\[]?\\s*(?:[A-Da-dEeĞğ]\\s*[).\\]:\\-–]|[1-5]\\s*[)\\]])\\s+")

    /**
     * Eski kural: rakamdan sonraki noktayı da işaret sayıyordu.
     *
     * Yalnızca o kuralın bozduğu kayıtları tanıyıp onarmak için duruyor
     * (bkz. `Repo.siraSayisiOnarimi`); yeni hiçbir okumada kullanılmıyor.
     */
    internal fun stripOptionPrefixLegacy(s: String): String =
        s.replace(LEGACY_OPTION_PREFIX, "").trim()

    private val LEGACY_OPTION_PREFIX =
        Regex("^\\s*[(\\[]?\\s*([A-Da-dEeĞğ]|[1-5])\\s*[).\\]:\\-–]\\s+")

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
