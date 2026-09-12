package com.emre.bilbakalim.arsiv.capture

import android.graphics.Rect
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.util.TurkishText

/**
 * Ekrandan toplanan metin parçalarını "soru + şıklar" haline getirir.
 *
 * Uygulamanın iç yapısını bilmediğimiz için konum tabanlı sezgiler kullanıyoruz:
 *  - Şıklar ekranın alt yarısında, birbirine benzer boyutta, 3-5 adet ve
 *    (erişilebilirlik yolunda) tıklanabilir kutulardır.
 *  - Soru, en üstteki şıkkın üzerinde kalan en uzun metindir.
 *
 * Bölge oranları Ayarlar'dan değiştirilebilir; böylece farklı bir arayüze
 * uyum sağlamak için kodu yeniden derlemek gerekmez.
 */
object QuestionParser {

    /** Teşhis ekranı için: son ayrıştırmanın neden başarısız olduğu. */
    @Volatile var lastReject: String? = null
        private set

    private fun reject(reason: String): Parsed? {
        lastReject = reason
        return null
    }

    data class Parsed(
        val question: String,
        val options: List<String>,
        val optionRects: List<Rect>,
        val category: String?,
        val confidence: Float
    ) {
        val key: String get() = TurkishText.fingerprint(question, options)
    }

    /** Sayaç, puan, buton gibi soru olmayan metinler. */
    private val CHROME = setOf(
        "puan", "altin", "altın", "can", "sure", "süre", "soru", "geri", "cikis", "çıkış",
        "ipucu", "joker", "sira", "sıra", "tur", "seviye", "devam", "atla", "kapat",
        "paylas", "paylaş", "tamam", "iptal", "menu", "menü", "ayarlar", "profil",
        "sonraki", "onceki", "önceki", "saniye", "sn", "dogru", "doğru", "yanlis", "yanlış",
        "skor", "toplam", "basla", "başla", "oyna", "tekrar", "cevap", "sonuc", "sonuç",
        "duello", "rakip", "sen", "bekleniyor", "hazir", "hazır",
        // Lobi / skor ekranlarının başlıkları
        "liderlik tablosu", "bilme orani", "bilme oranı", "oyunu baslat",
        "oyunu başlat", "son oyunlar", "puan durumu", "siralama", "sıralama",
        "istatistik", "basari", "başarı", "misafir", "rozet", "gorev", "görev",
        // Süre dolunca ekrana binen uyarı — soruya yapışmasın
        "sure bitti", "süre bitti", "sure doldu", "süre doldu", "zaman doldu"
    )

    fun parse(
        items: List<TextItem>,
        screenW: Int,
        screenH: Int,
        s: Prefs.Settings,
        fromAccessibility: Boolean
    ): Parsed? {
        if (items.isEmpty() || screenW <= 0 || screenH <= 0) return reject("ekranda metin yok")

        val cleaned = items
            .map { it.copy(text = TurkishText.cleanOcr(it.text)) }
            .filter { keep(it, screenH) }

        if (cleaned.size < 3) return reject("anlamlı metin 3'ten az")

        val optTop = (s.optionsTop * screenH).toInt()
        val optBottom = (s.optionsBottom * screenH).toInt()
        val qTop = (s.questionTop * screenH).toInt()
        val qBottom = (s.questionBottom * screenH).toInt()

        // --- 1. Şık adayları ---------------------------------------------------
        var optionPool = cleaned.filter { it.centerY in optTop..optBottom }

        // Erişilebilirlik yolunda tıklanabilir olanlar çok daha güvenilir.
        val clickablePool = optionPool.filter { it.clickable }
        if (fromAccessibility && clickablePool.size in 3..6) {
            optionPool = clickablePool
        }
        if (optionPool.size < 3) return reject("şık bölgesinde 3'ten az metin")

        val rows = groupIntoRows(optionPool, screenH)
        val ordered = rows.flatMap { row -> row.sortedBy { it.bounds.left } }

        // Şıklar birbirine benzer genişlikte olmalı; ortalamadan çok sapanı at.
        val candidates = trimOutliers(ordered)
        if (candidates.size < 3) return reject("şık adayı 3'ten az")

        val options = candidates.take(4)
        val optionTexts = options.map { TurkishText.stripOptionPrefix(it.text) }
            .filter { it.isNotBlank() }
        if (optionTexts.size < 3) return reject("şık metni 3'ten az")

        // Şıklar ekrana teker teker beliriyor. Yarısı gelmişken okursak soru
        // eksik şıkla kaydolur ve bir daha düzelmez; bu yüzden dördü de
        // görünene kadar bekliyoruz. Sonraki tarama saniyenin onda birinde
        // geleceği için bu bekleme fark edilmiyor.
        if (s.requireFourOptions && optionTexts.size < 4) {
            return reject("şıklar henüz tamamlanmadı (${optionTexts.size}/4)")
        }

        // --- 2. Soru metni -----------------------------------------------------
        val firstOptionTop = options.minOf { it.bounds.top }
        val questionPool = cleaned.filter {
            it.bounds.bottom <= firstOptionTop + 4 &&
                it.centerY in qTop..maxOf(qTop + 1, minOf(qBottom, firstOptionTop))
        }
        if (questionPool.isEmpty()) return reject("soru bölgesi boş")

        val question = assembleQuestion(questionPool)
            ?.let { TurkishText.stripLeadingChrome(it) }
            ?: return reject("soru metni kurulamadı")
        if (question.length < 8) return reject("soru metni çok kısa")

        // Soru metni şıklardan biriyle aynıysa yanlış ayrıştırdık demektir.
        if (optionTexts.any { TurkishText.similarity(it, question) > 0.9f })
            return reject("soru metni bir şıkla aynı")

        // Lobi/skor ekranlarındaki başlıkları elemek için: metin gerçekten
        // soru cümlesine benziyor mu?
        if (s.requireQuestionShape && !TurkishText.looksLikeQuestion(question)) {
            return reject("soru cümlesine benzemiyor: \"" + question.take(40) + "\"")
        }

        // --- 3. Kategori -------------------------------------------------------
        val category = if (s.autoDetectCategory) detectCategory(cleaned) else null

        // --- 4. Güven skoru ----------------------------------------------------
        var conf = 0.20f
        if (optionTexts.size == 4) conf += 0.26f else if (optionTexts.size == 3) conf += 0.10f
        if (TurkishText.looksLikeQuestion(question)) conf += 0.22f
        if (question.length in 15..260) conf += 0.10f
        if (fromAccessibility && options.all { it.clickable }) conf += 0.12f
        // Şıklar eşit aralıklarla dizilir — bu, gerçek bir soru ekranının en
        // güçlü işaretidir. Metin genişliği ise kelime uzunluğuna göre değişir,
        // o yüzden güven puanında kullanılmaz.
        if (evenlySpaced(options)) conf += 0.14f
        if (heightsConsistent(options)) conf += 0.08f
        if (optionTexts.all { it.length <= 70 }) conf += 0.05f
        if (question.contains('?')) conf += 0.05f
        conf = conf.coerceIn(0f, 1f)

        lastReject = null
        return Parsed(
            question = question,
            options = optionTexts,
            optionRects = options.map { Rect(it.bounds) },
            category = category,
            confidence = conf
        )
    }

    // ---------------------------------------------------------------------------

    private fun keep(item: TextItem, screenH: Int): Boolean {
        val t = item.text.trim()
        if (t.length < 2) return false

        // Durum çubuğu / gezinme çubuğu bölgesi
        if (item.bounds.bottom < screenH * 0.045f) return false
        if (item.bounds.top > screenH * 0.985f) return false

        // Sadece sayı / süre / yüzde / soru numarası balonları.
        // Parantez ve köşeli parantez de burada: "1)" ve "(44)" gibi parçalar
        // eskiden bu süzgeçten kaçıp soru metninin başına yapışıyordu.
        if (t.matches(Regex("^[\\d\\s:/.,%+\\-x×()\\[\\]|]+$"))) return false

        // "-4 sn", "+10 sn" gibi süre bildirimleri
        if (t.matches(Regex("(?i)^[+\\-]?\\d+\\s*(sn|sec|saniye)\\.?\$"))) return false

        // Çoğunluğu rakam olan parçalar (soru numarası şeridi vb.)
        val visible = t.count { !it.isWhitespace() }
        val digits = t.count { it.isDigit() }
        if (visible > 0 && digits.toFloat() / visible > 0.60f) return false

        val key = TurkishText.lower(t).trim(' ', ':', '.', '!', '-')
        if (key in CHROME) return false

        // Not: eşik eskiden 3'tü; "Üç", "Altı" gibi kısa şıklar bu yüzden
        // listeden düşüyordu. Artık yalnızca tek karakterlik parçalar eleniyor.
        if (key.length <= 1) return false
        return true
    }

    /** Dikey merkezleri birbirine yakın olanları aynı satıra koyar. */
    private fun groupIntoRows(items: List<TextItem>, screenH: Int): List<List<TextItem>> {
        val tol = (screenH * 0.035f).toInt().coerceAtLeast(12)
        val sorted = items.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<TextItem>>()
        for (it in sorted) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.last().centerY - it.centerY) <= tol) {
                row.add(it)
            } else {
                rows.add(mutableListOf(it))
            }
        }
        return rows
    }

    /**
     * Şıklar aynı yazı boyutuyla çizilir, ama metin genişliği kelime
     * uzunluğuna göre iki katına çıkabilir ("Simya" ile "Hermetik Felsefesi").
     * Bu yüzden eleme genişliğe değil, satır yüksekliğine bakarak yapılır —
     * yoksa uzun yazılmış doğru şık listeden düşer.
     */
    private fun trimOutliers(items: List<TextItem>): List<TextItem> {
        if (items.size <= 3) return items
        val heights = items.map { it.bounds.height() }.sorted()
        val median = heights[heights.size / 2].toFloat()
        if (median <= 0f) return items
        val kept = items.filter { it.bounds.height() / median in 0.60f..1.70f }
        return if (kept.size >= 3) kept else items
    }

    /** Şıklar arasındaki dikey boşluklar birbirine eşit mi? */
    private fun evenlySpaced(items: List<TextItem>): Boolean {
        if (items.size < 3) return false
        val tops = items.map { it.bounds.top }.sorted()
        val gaps = tops.zipWithNext { a, b -> (b - a).toFloat() }
        if (gaps.any { it <= 0f }) return false
        val avg = gaps.average().toFloat()
        if (avg <= 0f) return false
        return gaps.all { kotlin.math.abs(it - avg) / avg < 0.25f }
    }

    /** Şık satırlarının yükseklikleri birbirine yakın mı? */
    private fun heightsConsistent(items: List<TextItem>): Boolean {
        if (items.size < 2) return false
        val h = items.map { it.bounds.height().toFloat() }
        val avg = h.average().toFloat()
        if (avg <= 0f) return false
        return h.all { kotlin.math.abs(it - avg) / avg < 0.30f }
    }

    /**
     * Soru birden fazla satıra/düğüme bölünmüş olabilir. En uzun parçayı
     * çekirdek alıp ona dikey olarak bitişik parçaları okuma sırasında ekliyoruz.
     */
    private fun assembleQuestion(pool: List<TextItem>): String? {
        val core = pool.maxByOrNull { it.text.length } ?: return null
        if (core.text.length >= 25) {
            // Yeterince uzun: aynı bloktaki komşu satırları da al.
            val sameColumn = pool.filter {
                kotlin.math.abs(it.centerX - core.centerX) < core.bounds.width().coerceAtLeast(40)
            }.sortedBy { it.bounds.top }

            val joined = StringBuilder()
            var lastBottom = Int.MIN_VALUE
            for (it in sameColumn) {
                val gap = if (lastBottom == Int.MIN_VALUE) 0 else it.bounds.top - lastBottom
                val lineH = it.bounds.height().coerceAtLeast(1)
                if (lastBottom != Int.MIN_VALUE && gap > lineH * 1.6f) continue
                if (joined.isNotEmpty()) joined.append(' ')
                joined.append(it.text)
                lastBottom = it.bounds.bottom
            }
            val result = TurkishText.cleanOcr(joined.toString())
            return if (result.length >= core.text.length) result else core.text
        }
        // Hiçbiri uzun değilse en uzun ikisini birleştirmeyi dene.
        val top2 = pool.sortedByDescending { it.text.length }.take(2).sortedBy { it.bounds.top }
        val merged = TurkishText.cleanOcr(top2.joinToString(" ") { it.text })
        return merged.takeIf { it.length >= 8 }
    }

    private fun detectCategory(items: List<TextItem>): String? {
        for (item in items) {
            val key = TurkishText.normalizeKey(item.text)
            for (cat in Prefs.BILINEN_KATEGORILER) {
                if (key == TurkishText.normalizeKey(cat)) return cat
            }
        }
        return null
    }
}
