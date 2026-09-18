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

    /** Soru numarası ekranın sol kenarına bu kadar yakın olmalı. */
    private const val NO_MAX_X = 0.40f
    /** Soru metninin üstünde en fazla bu kadar uzakta olabilir. */
    private const val NO_MAX_GAP = 0.22f

    /** Teşhis ekranı için: son ayrıştırmanın neden başarısız olduğu. */
    @Volatile var lastReject: String? = null
        private set

    /**
     * [reason] ile reddeder.
     *
     * [optionsShort] "şık bölgesinden yeterince metin çıkmadı" demektir ve
     * yakalama tarafı yakın plan OCR'ı buna bakarak deniyor. Eskiden bu karar
     * red metninin başına bakarak veriliyordu ve yalnızca "3/4" hâlini
     * yakalıyordu: OCR dört sayı şıkkından ikisini birden düşürdüğünde red
     * "şık bölgesinde 3'ten az metin" oluyor, yakın plan hiç denenmiyor ve
     * soru ekranda cevapsız kalıyordu. Şıkları tamamen sayı olan sorularda
     * ML Kit yalıtık rakam bloğunu sıkça düşürdüğü için tam da olan buydu.
     *
     * [partial] o ana kadar okunabilen şık metinleri; boş olabilir.
     */
    private fun reject(
        reason: String,
        optionsShort: Boolean = false,
        partial: List<String> = emptyList()
    ): Parsed? {
        lastReject = reason
        lastOptionsShort = optionsShort
        // Eksik şık listesi yalnızca kendi reddine ait olmalı; başka bir
        // sebeple reddedilen kare onu bayat bırakmasın.
        lastPartial = partial
        return null
    }

    data class Parsed(
        val question: String,
        val options: List<String>,
        val optionRects: List<Rect>,
        val category: String?,
        val confidence: Float,
        /**
         * Soru kartının sol üstündeki sıra numarası ("2."), okunabildiyse.
         *
         * Turun kaçıncı sorusunda olduğumuzun tek kararlı işareti bu. Cevap
         * açılırken soru metni ve şıklar bozulabiliyor (puan balonu, renk
         * animasyonu, şıkların sönmesi); numara ise gerçekten yeni soruya
         * geçilene kadar aynı kalıyor.
         */
        val number: Int? = null
    ) {
        val key: String get() = TurkishText.fingerprint(question, options)
    }

    /**
     * Son "şıklar tamamlanmadı" reddinde bulunabilen şık metinleri.
     *
     * Kısa ve sayısal şıklarda ("2", "12") ML Kit metin bloğunu bütünüyle
     * düşürebiliyor; o zaman beklemenin faydası yok, şeridi büyütüp yeniden
     * okumak gerekiyor. Yakalama tarafı kararı buna bakarak veriyor.
     */
    @Volatile var lastPartial: List<String> = emptyList()
        private set

    /**
     * Son red, "şık bölgesinden yeterince metin çıkmadı" türünden miydi?
     *
     * Yakın plan OCR yalnızca bu durumda anlamlı: şıkların yerinde durduğunu
     * biliyoruz, okuyamıyoruz. Lobi ekranı ya da soru cümlesine benzemeyen
     * bir metin yüzünden gelen redler bunu tetiklememeli.
     */
    @Volatile var lastOptionsShort: Boolean = false
        private set

    /**
     * Son ayrıştırmada kaç OCR bloğu satırlarına ayrıldı.
     *
     * Teşhis için: sayı şıklarının tek bloğa yapışması gözle görülmeyen
     * bir arıza. Günlükte "blok bölündü" yazmıyorsa şıklar zaten ayrı
     * gelmiş demektir.
     */
    @Volatile var lastSplit: Int = 0
        private set

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

    /**
     * Ekranın altındaki joker düğmeleri ve bedelleri.
     *
     * Bunlar şık bölgesinin alt ucuna girebiliyor ve sayı süzgeci orada
     * gevşetildiği için şık sanılıyorlardı: arşivde gerçek şıklar yerine
     * "50" ve "50" yazan kayıtlar bundan.
     */
    private val JOKERS = setOf(
        "5050", "x2", "2x", "ciftcevap", "sorudegistir", "soruyudegistir",
        "yarimyarim", "elemejokeri", "degistir"
    )

    /**
     * Doğru cevaptan sonra ekrana düşen puan balonu ("+5", "5 +5").
     *
     * Şıkkın üstünde belirdiği için OCR bunu şık metniyle birleştirip ya da
     * onun yerine döndürebiliyor; arşivde şıkkı "5 +5" olan sorular bundan.
     * Kural: içinde en az bir rakam ve en az bir artı/eksi olan, bunun
     * dışında hiçbir harf içermeyen metinler.
     */
    private val SCORE_POPUP =
        Regex("^(?=[^0-9]*[0-9])(?=[^+\\-\u00b1]*[+\\-\u00b1])[0-9\\s+\\-\u00b1]+$")

    fun parse(
        raw: List<TextItem>,
        screenW: Int,
        screenH: Int,
        s: Prefs.Settings,
        fromAccessibility: Boolean
    ): Parsed? {
        if (raw.isEmpty() || screenW <= 0 || screenH <= 0) return reject("ekranda metin yok")

        val optTop = (s.optionsTop * screenH).toInt()
        val optBottom = (s.optionsBottom * screenH).toInt()
        val qTop = (s.questionTop * screenH).toInt()
        val qBottom = (s.questionBottom * screenH).toInt()

        // ML Kit alt alta duran kısa şıkları tek bloğa toplayabiliyor;
        // öyleyse şık bölgesindeki blokları satırlarına ayırıyoruz.
        var bolunen = 0
        val items = raw.flatMap {
            if (it.centerY !in optTop..optBottom) listOf(it)
            else splitStacked(it).also { parts -> if (parts.size > 1) bolunen++ }
        }
        lastSplit = bolunen

        // Süzgeç şık bölgesinde gevşiyor: orada sayılar şıkkın kendisi olabilir.
        val cleaned = items
            .map { it.copy(text = TurkishText.cleanOcr(it.text)) }
            .filter { keep(it, screenH, it.centerY in optTop..optBottom) }

        if (cleaned.size < 3) return reject("anlamlı metin 3'ten az", optionsShort = true)

        // --- 1. Şık adayları ---------------------------------------------------
        var optionPool = cleaned.filter { it.centerY in optTop..optBottom }

        // Erişilebilirlik yolunda tıklanabilir olanlar çok daha güvenilir.
        val clickablePool = optionPool.filter { it.clickable }
        if (fromAccessibility && clickablePool.size in 3..6) {
            optionPool = clickablePool
        }
        // Rozet şeridi elenince geriye üçten az metin kalıyorsa, şık
        // bölgesinde gerçek şık yok demektir — orada duran yalnızca joker
        // bedelleri. Eskiden bu durumda eleme geri alınıyor ve rozetler şık
        // adayı olarak kalıyordu; günlükteki «200»@%89 «200»@%89 «100»@%89
        // satırları bundan. Kaydı bozmuyordu ama kareyi boşa harcıyor ve
        // gerçek sebebi gizliyordu.
        val rozetsiz = dropNumberStrips(optionPool, screenH)
        if (rozetsiz.size < 3 && optionPool.size >= 3) {
            // Bölgede ne bulunduğu da yazılıyor. Bu satır olmadan red,
            // "gerçekten yalnızca rozet mi vardı yoksa şıklar tek parçaya
            // mı yapışmıştı" sorusunu yanıtlamıyordu — ve yanıt ikincisiydi.
            val bulunan = optionPool.joinToString(" ") { "«${it.text.take(20)}»" }
            return reject(
                "şık bölgesinde joker/puan rozetinden başka şık yok · $bulunan",
                optionsShort = true
            )
        }
        optionPool = rozetsiz
        if (optionPool.size < 3) return reject("şık bölgesinde 3'ten az metin", optionsShort = true)

        val rows = groupIntoRows(optionPool, screenH)
        val ordered = rows.flatMap { row -> row.sortedBy { it.bounds.left } }

        // Şıklar birbirine benzer genişlikte olmalı; ortalamadan çok sapanı at.
        val candidates = trimOutliers(ordered)
        if (candidates.size < 3) return reject("şık adayı 3'ten az", optionsShort = true)

        // Metin ve kutu aynı süzgeçten geçmeli. Eskiden boş metinler
        // ayıklanıyor ama kutuları listede kalıyordu; o zaman "2. şıkkın
        // metni" ile "2. şıkkın kutusu" başka şıklara ait oluyordu — dokunuş
        // bir şıkka, kaydedilen cevap başkasına gidiyordu.
        val options = candidates.take(4)
            .map { it to TurkishText.stripOptionPrefix(it.text) }
            .filter { (_, text) -> text.isNotBlank() }
        val optionTexts = options.map { it.second }
        if (optionTexts.size < 3) {
            return reject("şık metni 3'ten az", optionsShort = true, partial = optionTexts)
        }

        // Şıklar ekrana teker teker beliriyor. Yarısı gelmişken okursak soru
        // eksik şıkla kaydolur ve bir daha düzelmez; bu yüzden dördü de
        // görünene kadar bekliyoruz. Sonraki tarama saniyenin onda birinde
        // geleceği için bu bekleme fark edilmiyor.
        if (s.requireFourOptions && optionTexts.size < 4) {
            // Hangi şıkların bulunduğu ve nerede durdukları da yazılıyor:
            // "3/4" tek başına, dördüncünün OCR'da mı kaybolduğunu yoksa
            // henüz belirmediğini mi söylemiyordu. Konumlar ekran
            // yüksekliğinin yüzdesi; boşluğun nerede olduğu görülüyor.
            val bulunan = options.joinToString(" ") { (item, text) ->
                "«${text.take(16)}»@%${item.centerY * 100 / screenH}"
            }
            // Yakalama tarafı "bu eksik şık OCR'ın gözünden mi kaçtı" sorusunu
            // buradan cevaplıyor: kısa/sayısal şıklarda ML Kit bloğu düşürüyor.
            return reject(
                "şıklar henüz tamamlanmadı (${optionTexts.size}/4) · $bulunan",
                optionsShort = true,
                partial = optionTexts
            )
        }

        // --- 2. Soru metni -----------------------------------------------------
        val firstOptionTop = options.minOf { it.first.bounds.top }
        val questionPool = cleaned.filter {
            it.bounds.bottom <= firstOptionTop + 4 &&
                it.centerY in qTop..maxOf(qTop + 1, minOf(qBottom, firstOptionTop))
        }
        if (questionPool.isEmpty()) return reject("soru bölgesi boş")

        val question = assembleQuestion(questionPool)
            ?.let { TurkishText.stripQuestionChrome(it) }
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
        if (fromAccessibility && options.all { it.first.clickable }) conf += 0.12f
        // Şıklar eşit aralıklarla dizilir — bu, gerçek bir soru ekranının en
        // güçlü işaretidir. Metin genişliği ise kelime uzunluğuna göre değişir,
        // o yüzden güven puanında kullanılmaz.
        val optionItems = options.map { it.first }
        if (evenlySpaced(optionItems)) conf += 0.14f
        if (heightsConsistent(optionItems)) conf += 0.08f
        if (optionTexts.all { it.length <= 70 }) conf += 0.05f
        if (question.contains('?')) conf += 0.05f
        conf = conf.coerceIn(0f, 1f)

        lastReject = null
        lastOptionsShort = false
        lastPartial = emptyList()
        return Parsed(
            question = question,
            options = optionTexts,
            optionRects = options.map { Rect(it.first.bounds) },
            category = category,
            confidence = conf,
            number = detectQuestionNumber(items, screenW, screenH, questionPool.minOf { it.bounds.top })
        )
    }

    /** Soru numarası balonu: "2", "2.", "2)". */
    private val QUESTION_NO = Regex("^(\\d{1,2})\\s*[.)]?\$")

    /**
     * Soru kartının sol üstündeki sıra numarasını okur.
     *
     * Ekranda başka sayılar da var: altın, yıldız, sayaç, ve en tepede turun
     * ilerleme şeridi ("1 2 3 4 5 6 7"). Doğru olanı üç işaretle ayırıyoruz:
     *
     *  • Aynı satırda üç ya da daha fazla sayı varsa o, ilerleme şerididir.
     *  • Numara solda durur — sağdaki aynı hizadaki sayı geri sayım sayacı.
     *  • Soru metnine en yakın olandır, ve ona yakın olmak zorundadır.
     *
     * Son kural en önemlisi: onsuz, numara bir karede okunamadığında altın
     * sayısı gibi **hiç değişmeyen** bir sayı seçilebilir, o da "soru hâlâ
     * aynı" demek olur ve yakalama tamamen durur. Emin olamadığımızda null
     * dönüyoruz; çağıran taraf o zaman numarasız çalışıyor.
     */
    private fun detectQuestionNumber(
        raw: List<TextItem>,
        screenW: Int,
        screenH: Int,
        questionTop: Int
    ): Int? {
        val numeric = raw.filter { QUESTION_NO.matches(it.text.trim()) }
        if (numeric.isEmpty()) return null

        val strip = groupIntoRows(numeric, screenH)
            .filter { it.size >= 3 }
            .flatten()
            .toSet()

        val badge = numeric
            .filterNot { it in strip }
            .filter { it.centerX < screenW * NO_MAX_X }
            .filter { it.bounds.bottom <= questionTop }
            .filter { questionTop - it.centerY <= screenH * NO_MAX_GAP }
            .maxByOrNull { it.centerY }
            ?: return null

        return QUESTION_NO.find(badge.text.trim())?.groupValues?.get(1)?.toIntOrNull()
    }

    // ---------------------------------------------------------------------------

    /** Sadece sayı / süre / yüzde / soru numarası balonları. */
    private val ONLY_NUMERIC = Regex("^[\\d\\s:/.,%+\\-x×()\\[\\]|]+\$")
    /** "-4 sn", "+10 sn" gibi süre bildirimleri. */
    private val SECONDS = Regex("(?i)^[+\\-]?\\d+\\s*(sn|sec|saniye)\\.?\$")
    /** Sadece rakamdan oluşan rozet (joker bedeli vb.). */
    private val NUMBER_ONLY = Regex("^[\\d\\s.,]+\$")

    private fun keep(item: TextItem, screenH: Int, inOptionArea: Boolean): Boolean {
        // Durum çubuğu / gezinme çubuğu bölgesi
        if (item.bounds.bottom < screenH * 0.045f) return false
        if (item.bounds.top > screenH * 0.985f) return false
        return !isChrome(item.text, inOptionArea)
    }

    /**
     * Metin, konumundan bağımsız olarak, soru/şık olmaya aday mı yoksa arayüz
     * parçası mı?
     *
     * Sayı kuralları yalnızca **şık bölgesinin dışında** işletiliyor ve bunun
     * somut bir sebebi var: matematik sorularında şıkların kendisi sayıdır
     * ("25", "55", "5", "15"). Süzgeç bütün ekrana aynı sertlikte
     * uygulandığında bu şıkların dördü birden eleniyor, geriye üçten az metin
     * kalıyor ve soru hiç yakalanamıyordu — otomatik mod da dokunacak bir şık
     * bulamadığı için ekranda öylece bekliyordu.
     *
     * Sayaç, puan, süre ve soru numarası balonları şık bölgesinin dışında
     * kaldığı için süzgeç onlar üzerinde olduğu gibi duruyor. Aynı "55" metni
     * ekranın tepesinde sayaçtır ve elenir, şık bölgesinde şıktır ve kalır.
     */
    internal fun isChrome(raw: String, inOptionArea: Boolean): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return true
        // Salt noktalama / süsleme parçaları her yerde çöptür.
        if (t.none { it.isLetterOrDigit() }) return true
        // Süre bildirimi şık olamaz.
        if (t.matches(SECONDS)) return true
        // Doğru cevaptan sonra düşen puan balonu.
        if (t.matches(SCORE_POPUP)) return true
        // Alttaki joker düğmeleri.
        if (TurkishText.normalizeKey(t) in JOKERS) return true

        if (!inOptionArea) {
            if (t.length < 2) return true
            // Parantez ve köşeli parantez de burada: "1)" ve "(44)" gibi
            // parçalar eskiden bu süzgeçten kaçıp soru metnine yapışıyordu.
            if (t.matches(ONLY_NUMERIC)) return true
            // Çoğunluğu rakam olan parçalar (soru numarası şeridi vb.)
            val visible = t.count { !it.isWhitespace() }
            val digits = t.count { it.isDigit() }
            if (visible > 0 && digits.toFloat() / visible > 0.60f) return true
        }

        val key = TurkishText.lower(t).trim(' ', ':', '.', '!', '-')
        if (key.isEmpty()) return true
        if (key in CHROME) return true

        // Not: eşik eskiden 3'tü; "Üç", "Altı" gibi kısa şıklar bu yüzden
        // listeden düşüyordu. Şık bölgesinin dışında tek karakterlik parçalar
        // hâlâ eleniyor; bölgenin içinde "5" gibi tek haneli şıklar geçerli.
        return !inOptionArea && key.length <= 1
    }

    /**
     * Yan yana dizilmiş sayı rozeti şeritlerini atar.
     *
     * Şık bölgesinin alt ucuna joker bedelleri gibi rozetler girebiliyor
     * ("200  200  100"). Sayı süzgeci orada gevşetildiği için artık bunlar da
     * geçiyor. Ayırt edici işaret dizilim: şıklar alt alta tek tek durur,
     * rozetler ise aynı satırda üç ya da daha fazla sayıdır.
     *
     * Elemek listeyi üçün altına düşürecekse hiçbir şey atılmaz — o durumda
     * rozet sandığımız şeyler büyük ihtimalle gerçekten şıklardır.
     */
    private fun dropNumberStrips(items: List<TextItem>, screenH: Int): List<TextItem> {
        if (items.size < 3) return items
        return groupIntoRows(items, screenH)
            .filterNot { row -> isNumberStrip(row.map { it.text }) }
            .flatten()
    }

    /**
     * Aynı satırdaki metinler bir rozet şeridi mi?
     *
     * Joker bedelleri ("200 200 100") yan yana üç sayı olarak duruyor.
     * Gerçek sayı şıkları ise alt alta, satır başına bir tane — bu yüzden
     * "aynı satırda üç veya daha fazla salt sayı" ayrımı ikisini ayırmaya
     * yetiyor ve matematik sorularının şıklarını elemiyor.
     */
    internal fun isNumberStrip(texts: List<String>): Boolean =
        texts.size >= 3 && texts.all { it.trim().matches(NUMBER_ONLY) }

    /**
     * Bir OCR bloğunu, satırları ayrı ayrı şık gibi duruyorsa böler.
     *
     * ML Kit'in blok birleştirmesi çok satırlı soruyu tek parçada tuttuğu
     * için genelde işimize geliyor. Ama şıkları kısa sayılar olan
     * sorularda ("240 / 1 / 365 / 24") dört rakam alt alta, aynı yazı
     * boyunda ve aynı eksende durduğu için ML Kit dördünü TEK bloğa
     * topluyor. Blok metni "240\n1\n365\n24" oluyor; [TurkishText.cleanOcr]
     * satır sonlarını boşluğa çevirince de elimizde dört şıkkın yerine
     * "240 1 365 24" yazan tek bir parça kalıyor — üstelik kutusu dört
     * şıkkı birden kapsıyor. Ayrıştırıcı şık bulamıyor, bot hiçbir tuşa
     * basmıyordu.
     */
    private fun splitStacked(item: TextItem): List<TextItem> {
        val l = item.lines
        if (l.size < 2) return listOf(item)
        val kumeler = clusterRows(l.map { it.bounds.top }, l.map { it.bounds.bottom })
        if (kumeler.size < 2) return listOf(item)
        return kumeler.map { küme ->
            val grup = küme.map { l[it] }
            TextItem(
                text = grup.joinToString(" ") { it.text },
                bounds = Rect(
                    grup.minOf { it.bounds.left },
                    grup.minOf { it.bounds.top },
                    grup.maxOf { it.bounds.right },
                    grup.maxOf { it.bounds.bottom }
                ),
                clickable = item.clickable
            )
        }
    }

    /**
     * Blok satırlarını "aynı kutuda duranlar" diye gruplar.
     *
     * Ayrım aradaki boşlukta. Ölçülen gerçek ekranda (1080x2400):
     *
     *  • Ayrı şık kutularındaki satırlar: metin yüksekliği 36 piksel,
     *    aralarındaki boşluk 171 piksel — oran **4,75**.
     *  • Sarmalanmış soru paragrafının satırları: yükseklik 38 piksel,
     *    boşluk 34 piksel — oran **0,89**.
     *
     * Eşik ikisinin ortasında duruyor, ikisine de geniş pay bırakarak.
     *
     * Kümeleme, tek tek bölmekten daha doğru: uzun bir şık kendi kutusunda
     * iki satıra sarmalanmışsa o iki satır birlikte kalır, kutular arası
     * boşluklarda ise bölünür.
     */
    internal fun clusterRows(tops: List<Int>, bottoms: List<Int>): List<IntRange> {
        if (tops.isEmpty() || tops.size != bottoms.size) return emptyList()
        val heights = tops.indices.map { bottoms[it] - tops[it] }
        if (heights.any { it <= 0 }) return listOf(tops.indices)
        val ortanca = heights.sorted()[heights.size / 2]
        if (ortanca <= 0) return listOf(tops.indices)

        val out = ArrayList<IntRange>()
        var start = 0
        for (i in 0 until tops.size - 1) {
            if (tops[i + 1] - bottoms[i] >= ortanca * SPLIT_MIN_GAP) {
                out.add(start..i)
                start = i + 1
            }
        }
        out.add(start..tops.lastIndex)
        return out
    }

    /**
     * Satırın "ayrı kutuda" sayılması için gereken boşluk / satır
     * yüksekliği oranı. Ölçülen değerler: ayrı kutu 4,75 — paragraf 0,89.
     */
    private const val SPLIT_MIN_GAP = 2.0

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
