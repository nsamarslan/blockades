package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.emre.bilbakalim.arsiv.util.Importers
import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlinx.coroutines.flow.Flow

/**
 * Kaydetme mantığının tek kapısı. Aynı sorunun tekrar tekrar yazılmasını
 * iki aşamada engeller:
 *   1. Parmak izi (kesin eşleşme, veritabanı seviyesinde tekil indeks)
 *   2. Bulanık benzerlik (OCR bir iki harfi yanlış okuduysa)
 */
class Repo private constructor(context: Context) {

    private val db = ArsivDatabase.get(context)
    private val dao = db.questionDao()
    private val prefs = Prefs.get(context)

    val totalCount: Flow<Int> = dao.observeTotal()
    val answeredCount: Flow<Int> = dao.observeAnswered()
    val categoryCounts: Flow<List<CategoryCount>> = dao.observeCategoryCounts()
    fun recent(limit: Int = 30): Flow<List<QuestionEntity>> = dao.observeRecent(limit)
    fun observeById(id: Long) = dao.observeById(id)
    fun search(q: String, cat: String?, onlyUnanswered: Boolean) =
        dao.search(q, cat, if (onlyUnanswered) 1 else 0)

    sealed interface SaveResult {
        data class Inserted(val id: Long) : SaveResult
        /**
         * [onarim] doluysa kayıt bozuk bulunup ekrandaki okumadan onarıldı.
         * [yol] kaydın nasıl bulunduğu: parmak izi (birebir) ya da benzerlik.
         */
        data class Duplicate(
            val id: Long,
            val onarim: String? = null,
            val yol: String = PARMAK_IZI
        ) : SaveResult
        data object Rejected : SaveResult
    }

    suspend fun save(
        question: String,
        options: List<String>,
        category: String?,
        source: CaptureSource,
        confidence: Float,
        screenshotPath: String?
    ): SaveResult {
        val q = TurkishText.cleanOcr(question)
        val opts = options.map { TurkishText.cleanOcr(TurkishText.stripOptionPrefix(it)) }
            .filter { it.isNotBlank() }

        if (q.length < 8 || opts.size < 2) return SaveResult.Rejected

        val fp = TurkishText.fingerprint(q, opts)

        dao.byFingerprint(fp)?.let { mevcut ->
            if (!mevcut.edited) {
                // Parmak izi işaretleri görmüyor ("-16" ile "16" aynı anahtar);
                // işareti silinmiş eski kayıt tam burada bulunuyor.
                onarimBul(mevcut, opts)?.let { (neden, onarim) ->
                    siklariOnar(mevcut, opts, fp, onarim)
                    return SaveResult.Duplicate(mevcut.id, "şıklar $neden ile yeniden yazıldı")
                }
                // Bu kaydın parmak izi tam bu okumadan geliyor, ama metni
                // sonradan başka bir sorunun metniyle değiştirilmiş olabilir:
                // eski birleşme kuralı "cos" / "cot" / "tan" gibi soruları tek
                // kayıtta topluyor ve daha uzun metni alıyordu. Metin geri
                // alınmazsa o başka soru bu kayda düşmeye devam ederdi.
                if (TekrarSorgusu.ayriMetinler(mevcut.questionText, q)) {
                    dao.replaceText(mevcut.id, q)
                    adaylariUnut()
                    return SaveResult.Duplicate(
                        mevcut.id, "soru metni geri alındı (başka bir sorunun metni yazılmıştı)"
                    )
                }
            }
            return SaveResult.Duplicate(mevcut.id, yol = PARMAK_IZI)
        }

        // Bulanık kontrol: son kayıtlar + cevabı eksik olan bütün kayıtlar.
        //
        // İkincisi olmadan arşiv birkaç yüz soruyu geçtiğinde şu oluyordu:
        // aylar önce yakalanmış ama cevabı kaçmış bir soru yeniden çıkıyor,
        // OCR bir harfi farklı okuduğu için parmak izi tutmuyor, eski satır
        // da pencerenin dışında kaldığı için bulunamıyor — ikinci bir satır
        // açılıyor ve cevap ona yazılıyor. Eski satır sonsuza kadar "cevabı
        // eksik" olarak duruyordu. Artık o satır bulunup doldurulacak.
        //
        val sorgu = TekrarSorgusu(q, opts)
        val hit = tekrarAdaylari().firstOrNull { sorgu.matches(it) }
        val old = hit?.let { dao.byId(it.id) }
        if (old != null) {
            if (!old.edited) {
                // Eski şık işareti kuralı sıra sayılarını siliyordu ("1. Dönem"
                // → "Dönem"); o kayıtlar kendiliğinden düzelmiyordu, çünkü
                // şıklar yalnızca liste kısaysa tamamlanıyor. Ekrandaki okuma
                // bozulmanın tam karşılığıysa şıklar ondan yeniden yazılıyor.
                onarimBul(old, opts)?.let { (neden, onarim) ->
                    siklariOnar(old, opts, fp, onarim)
                    return SaveResult.Duplicate(old.id, "şıklar $neden ile yeniden yazıldı", BENZERLIK)
                }
                // Hangi metin daha temiz? Arayüz uyarısı içermeyen kazanır;
                // ikisi de temizse daha uzun olanı alırız.
                val oldDirty = TurkishText.hasChromePhrase(old.questionText)
                val newDirty = TurkishText.hasChromePhrase(q)
                val takeNew = when {
                    oldDirty && !newDirty -> true
                    !oldDirty && newDirty -> false
                    else -> q.length > old.questionText.length
                }
                if (takeNew && q != old.questionText) {
                    dao.replaceText(old.id, q)
                    adaylariUnut()
                }
                if (old.options.size < opts.size) {
                    // Eksik şıklar tamamlanıyor — ama yeni liste o anki
                    // ekranın sırasıyla geliyor. Kayıtta zaten bir doğru
                    // cevap varsa sırası kayabilir; bu yüzden metnini
                    // tutup yeni listede yeniden arıyoruz. Yoksa doğru
                    // cevap sessizce yanlış şıkkı göstermeye başlıyordu.
                    val merged = old.copy(
                        optionA = opts.getOrNull(0) ?: old.optionA,
                        optionB = opts.getOrNull(1) ?: old.optionB,
                        optionC = opts.getOrNull(2) ?: old.optionC,
                        optionD = opts.getOrNull(3) ?: old.optionD
                    )
                    // Metin bulunamazsa eski SIRAYA düşmek yasak: o sıra
                    // eski (kısa) listeye aitti, yeni listede bambaşka bir
                    // şıkkı gösterir. Kayıtlı cevap böyle sessizce başka bir
                    // şıkka kayıyor ve otomatik mod ondan sonra hep ona
                    // basıyordu. Bulunamıyorsa cevabı boşaltıyoruz; bir
                    // sonraki karşılaşmada renk okuması yeniden öğretir.
                    val remapped = TurkishText.matchIndex(merged.options, old.correctText)
                    if (remapped == null && old.correctIndex != null) {
                        Log.w(TAG, "Şıklar tamamlandı ama #${old.id} cevabı " +
                            "«${old.correctText}» yeni listede yok; cevap boşaltıldı")
                    }
                    dao.update(
                        merged.copy(
                            correctIndex = remapped,
                            answerSource = if (remapped == null) null else merged.answerSource
                        )
                    )
                    adaylariUnut()
                }
            }
            return SaveResult.Duplicate(old.id, yol = BENZERLIK)
        }

        val entity = QuestionEntity(
            questionText = q,
            optionA = opts.getOrNull(0),
            optionB = opts.getOrNull(1),
            optionC = opts.getOrNull(2),
            optionD = opts.getOrNull(3),
            // Tarama kategori adını taramanın başında okuyor; ad o sırada
            // değiştirildiyse eski ad arşive geri yazılmasın.
            category = prefs.state.value.kategoriListesi.guncelAd(category),
            source = source.name,
            confidence = confidence,
            fingerprint = fp,
            screenshotPath = screenshotPath
        )
        val id = dao.insertIgnore(entity)
        return if (id > 0) {
            // En yeni kayıt önde: veritabanı sorgusunun sırası da bu.
            adaylar?.let { adaylar = listOf(TekrarAdayi(id, q, opts)) + it }
            Log.i(TAG, "Yeni soru kaydedildi #$id: ${q.take(50)}")
            SaveResult.Inserted(id)
        } else {
            SaveResult.Duplicate(dao.byFingerprint(fp)?.id ?: -1L)
        }
    }


    /** Eski kuralların bozduğu şıkları tanıyan onarımlardan tutan ilki. */
    private fun onarimBul(old: QuestionEntity, opts: List<String>): Pair<String, Onarim>? {
        siraSayisiOnarimi(old.options, old.correctText, opts)?.let { return SIRA_SAYISI_ONARIMI to it }
        isaretOnarimi(old.options, old.correctText, opts)?.let { return ISARET_ONARIMI to it }
        ciftOkumaOnarimi(old.options, old.correctText, opts)?.let { return CIFT_OKUMA_ONARIMI to it }
        return null
    }

    /**
     * Bozuk bulunan kaydın şıklarını ekrandaki okumayla değiştirir. Doğru
     * cevap [onarim]'daki yeni sırasıyla yazılıyor; belirsizse boşaltılıyor,
     * bir sonraki renk okuması yeniden öğretiyor.
     */
    private suspend fun siklariOnar(old: QuestionEntity, opts: List<String>, fp: String, onarim: Onarim) {
        val onarilmis = old.copy(
            optionA = opts.getOrNull(0),
            optionB = opts.getOrNull(1),
            optionC = opts.getOrNull(2),
            optionD = opts.getOrNull(3),
            correctIndex = onarim.dogru,
            answerSource = if (onarim.dogru == null) null else old.answerSource,
            fingerprint = fp
        )
        // Parmak izi başka bir satırda duruyorsa tekil indeks yazmayı
        // reddeder; o zaman eski parmak iziyle.
        if (runCatching { dao.update(onarilmis) }.isFailure) {
            runCatching { dao.update(onarilmis.copy(fingerprint = old.fingerprint)) }
        }
        adaylariUnut()
        Log.i(TAG, "#${old.id} şıkları onarıldı: ${old.options} → $opts")
    }

    /**
     * Tekrar denetiminin karşılaştırdığı arşiv, önceden hesaplanmış hâliyle.
     *
     * Her yeni okuma arşivin tamamıyla karşılaştırılıyor; eskiden bunun için
     * her seferinde tüm satırlar veritabanından okunup metinleri yeniden
     * işleniyordu (bkz. [TekrarAdayi]). Liste ilk ihtiyaçta kuruluyor, yeni
     * kayıt başına ekleniyor; metni ya da şıkları değiştiren her yazımda
     * atılıyor ve bir sonraki ihtiyaçta yeniden kuruluyor. Sıra
     * veritabanındakiyle aynı: en yeni kayıt önde.
     */
    @Volatile private var adaylar: List<TekrarAdayi>? = null

    private suspend fun tekrarAdaylari(): List<TekrarAdayi> =
        adaylar ?: dao.dedupCandidates(DEDUP_POOL).map { TekrarAdayi(it) }.also { adaylar = it }

    /** Metni ya da şıkları değişen bir yazımdan sonra: önbellek yeniden kurulsun. */
    private fun adaylariUnut() {
        adaylar = null
    }

    // --- İçe aktarma ---------------------------------------------------------

    sealed interface ImportResult {
        /** [total] dosyadaki okunabilir satır sayısı. */
        data class Ok(
            val total: Int,
            val added: Int,
            val merged: Int,
            val skipped: Int
        ) : ImportResult

        data class Failed(val reason: String) : ImportResult
    }

    /**
     * JSON yedeğini arşive katar.
     *
     * Var olanın üstüne yazmaz, ekler: aynı soru zaten arşivdeyse yalnızca
     * eksikleri tamamlanır (bilinmeyen cevap, eksik şık, boş kategori).
     * Sayaçlarda büyük olan alındığı için aynı dosyayı iki kez içe aktarmak
     * hiçbir şeyi bozmaz — ikinci seferde her şey "değişmedi" diye geçer.
     */
    suspend fun importJson(text: String): ImportResult {
        val rows = try {
            Importers.parse(text)
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed(e.message ?: "Dosya okunamadı")
        }
        if (rows.isEmpty()) return ImportResult.Failed("Dosyada okunabilir soru yok")

        var added = 0
        var merged = 0
        var skipped = 0

        // Adı değiştirilmiş bir kategori eski yedekte eski adıyla duruyor;
        // olduğu gibi alınsa arşive geri dönerdi.
        val kategoriler = prefs.state.value.kategoriListesi
        for (ham in rows) {
            val row = ham.copy(category = kategoriler.guncelAd(ham.category))
            val incoming = Importers.toEntity(row)
            // İçe aktarma her satırda arşivi değiştirebildiği için önbelleği
            // kullanmıyor; her satır güncel arşivle karşılaştırılıyor.
            val existing = dao.byFingerprint(incoming.fingerprint)
                ?: TekrarSorgusu(row.question, row.options).let { sorgu ->
                    dao.dedupCandidates(DEDUP_POOL).firstOrNull { sorgu.matches(TekrarAdayi(it)) }
                        ?.let { dao.byId(it.id) }
                }

            if (existing == null) {
                if (dao.insertIgnore(incoming) > 0) added++ else skipped++
                continue
            }

            val updated = Importers.merge(existing, row)
            if (updated == existing) {
                skipped++
                continue
            }
            // Şıklar tamamlandıysa parmak izi de değişir; o parmak izi başka
            // bir satırda duruyorsa tekil indeks yazmayı reddeder. Böyle bir
            // durumda kaydı eski parmak iziyle güncelliyoruz: birleşmenin
            // geri kalanı yine de kazanç.
            val ok = runCatching { dao.update(updated) }.isSuccess
            if (!ok) {
                runCatching { dao.update(updated.copy(fingerprint = existing.fingerprint)) }
            }
            merged++
        }

        adaylariUnut()
        Log.i(TAG, "İçe aktarma: $added yeni, $merged birleşti, $skipped değişmedi")
        return ImportResult.Ok(rows.size, added, merged, skipped)
    }

    /**
     * Yeni bir karşılaşmayı sayar.
     *
     * Bunu bilerek [save] dışına aldık: aynı soru ekranı saniyede birkaç kez
     * taranıyor ve her tarama küçük OCR farkları yüzünden ayrı bir kayıt
     * denemesi oluyordu. Sayaç orada artırılınca bir kez gördüğün soru
     * "3 kez çıktı" görünüyordu. Artık yalnızca gerçekten yeni bir soruya
     * geçildiğinde çağrılıyor.
     */
    suspend fun countEncounter(id: Long) = dao.bumpSeen(id)

    /**
     * Cevap açıldığında çağrılır. Doğru cevabı işler ve — süre dolmadıysa —
     * bunu bir "deneme" olarak sayıp doğru bilip bilmediğini kaydeder.
     * Böylece soru başına "kaç kez çıktı, kaçında bildin" çıkarılabiliyor.
     *
     * [correctIndex] **ekrandaki** sıradır, kayıttaki değil. Oyun şıkları her
     * turda karıştırdığı için bu iki sıra birbirini tutmaz: ilk karşılaşmada
     * 1. sırada duran şık ikinci karşılaşmada 3. sırada olabilir. Bu yüzden
     * sırayı değil, o sıradaki **metni** alıp kayıttaki listede arıyoruz.
     * [screenOptions] boş geçilirse (ya da eşleşme bulunamazsa) sıra olduğu
     * gibi kullanılır; bu yalnızca ilk kayıtta güvenlidir, orada iki liste
     * zaten aynıdır.
     *
     * Cevabı ilk karşılaşmada yakalayamamış olsak bile bu çağrı sonraki
     * karşılaşmada aynı satırı doldurur; soru bir daha "cevabı eksik"
     * görünmez.
     */
    suspend fun recordReveal(
        id: Long,
        correctIndex: Int,
        screenOptions: List<String> = emptyList(),
        userWasRight: Boolean,
        countAsAttempt: Boolean,
        evidence: AnswerEvidence = AnswerEvidence.GREEN
    ) {
        if (correctIndex !in 0..3) return
        val row = dao.byId(id) ?: return

        // Zayıf bir okuma, güçlü kanıtla yazılmış bir cevabın üstüne yazmasın.
        // Kayıtta zaten cevap varsa ve elimizdeki kanıt daha zayıfsa
        // dokunmuyoruz; sayaçlar yine de işleniyor, çünkü karşılaşma gerçek.
        val keepStored = shouldKeepStored(row.correctIndex, row.answerSource, evidence)

        // Ekrandaki doğru şıkkın METNİNİ arşivdeki listede ara.
        //
        // Kritik: burada sıraya düşmek yasak. Oyun şıkları her turda
        // karıştırdığı için, ekrandaki 2. şık ile kayıttaki 2. şık aynı
        // şey değildir. Metin eşleşmiyorsa cevap yazmıyoruz — bir sonraki
        // karşılaşmada zaten yeniden okunacak; yanlış cevap yazıp otomatik
        // modun her turda o yanlışa basmasına sebep olmaktan iyidir.
        //
        // screenOptions boş bırakılırsa (eski çağrılar) sıra olduğu gibi
        // kullanılır; bu yalnızca ilk kayıtta güvenlidir, orada iki liste
        // zaten aynıdır.
        val stored: Int = if (screenOptions.isEmpty()) {
            correctIndex
        } else {
            val screenText = screenOptions.getOrNull(correctIndex)
            if (screenText.isNullOrBlank()) {
                Log.w(TAG, "Cevap #$id yazılamadı: ekranda ${correctIndex}. şıkkın metni yok")
                return
            }
            TurkishText.matchIndex(row.options, screenText) ?: run {
                Log.w(
                    TAG,
                    "Cevap #$id yazılamadı: «${screenText.take(40)}» arşivde bulunamadı"
                )
                return
            }
        }
        if (stored !in row.options.indices) {
            Log.w(TAG, "Cevap #$id yazılamadı: şık listesi tutmuyor")
            return
        }

        if (!row.edited && !keepStored) dao.setCorrect(id, stored, evidence.label)
        if (countAsAttempt) dao.recordAttempt(id, if (userWasRight) 1 else 0)
        Log.i(TAG, "Cevap #$id -> ${'A' + stored}, kullanıcı ${if (userWasRight) "bildi" else "bilemedi"}")
    }

    /**
     * Doğru cevabı ne kadar sağlam bir gözlemden öğrendik.
     *
     * Buna ihtiyaç duymamızın sebebi: zayıf bir okuma, daha önce kesin
     * gözlemle yazılmış doğru cevabın üstüne yazabiliyordu. Arşive bir kez
     * yanlış cevap girdiğinde otomatik mod her turda ona basmaya devam
     * ettiği için hata kendini besliyor.
     */
    enum class AnswerEvidence(val label: String, val strength: Int) {
        /** Kırmızı da görüldü: yeşil olan kesinlikle doğru cevaptır. */
        CERTAIN("renk (kesin)", 3),
        /** Yalnızca karar yeşili görüldü, kırmızı yok. */
        GREEN("renk", 2),
        /** Süre doldu, ekran karardı, ayrışan şık işaretlendi. */
        TIMEOUT("süre doldu", 2),
        /**
         * Dokunulan şık karar açılmadan öylece kaldı. En zayıf kanıt:
         * "dokunduğuna göre doğrusunu biliyordun" varsayımına dayanıyor.
         */
        TOUCH("dokunuş", 1)
    }



    /**
     * [siraSayisiOnarimi] ve [isaretOnarimi] sonucu: kayıt onarılacak. [dogru] doğru cevabın
     * ekrandaki yeni sırası; bilinmiyorsa ya da belirsizse null.
     */
    internal data class Onarim(val dogru: Int?)

    /** [knownAnswerOnScreen] sonucu. */
    sealed interface KnownAnswer {
        /** Arşivdeki doğru cevap ekranda bu sırada duruyor. */
        data class OnScreen(val index: Int) : KnownAnswer
        /**
         * Arşivde cevap var ama ekrandaki şıkların hiçbirine benzemiyor.
         *
         * "Arşivde cevap yok" ile karıştırılmamalı: bu bir arıza işareti —
         * ya OCR şıkları bozuk okumuş ya da kayıttaki metin ekrandakinden
         * gerçekten farklı. İkisi de tek satır günlükle ayırt edilebilsin
         * diye ayrı duruyor; yoksa bot sessizce rastgeleye düşüyor ve
         * "neden bilinen cevaba basmadı" sorusunun izi kalmıyor.
         */
        data class Unmatched(val text: String?) : KnownAnswer
        /** Soru arşivde yok ya da cevabı henüz bilinmiyor. */
        data object None : KnownAnswer
    }

    /**
     * Arşivdeki doğru cevabın **o anki ekrandaki** sırası.
     *
     * Otomatik mod bunu kullanıyor: soruyu daha önce görmüşsek rastgele
     * seçmek yerine doğru şıkka basıyoruz. Şıklar her turda karıştığı için
     * kayıttaki sıra doğrudan kullanılamaz — kayıttaki doğru cevabın
     * **metnini** alıp ekrandaki listede arıyoruz.
     */
    suspend fun knownAnswerOnScreen(id: Long, screenOptions: List<String>): KnownAnswer {
        val row = dao.byId(id) ?: return KnownAnswer.None
        if (row.correctIndex == null) return KnownAnswer.None

        // Kayıtlı sıra, kaydın kendi şık listesinin dışını gösteriyorsa
        // metni de çıkaramayız; bu bozuk bir satırdır.
        val text = row.correctText ?: return KnownAnswer.Unmatched(null)

        val index = TurkishText.matchIndex(screenOptions, text)
        return if (index != null) KnownAnswer.OnScreen(index) else KnownAnswer.Unmatched(text)
    }

    /**
     * Bu parmak izi arşivde var mı?
     *
     * Yakalama tarafı bunu "ikinci okumayı beklemeye gerek var mı" sorusunu
     * yanıtlamak için kullanıyor: parmak izi soru metni ve sıralanmış
     * şıklardan hesaplandığı için, bozuk bir OCR okuması daha önce
     * kaydedilmiş bir kaydın izini birebir üretemez.
     */
    suspend fun isKnownFingerprint(fp: String): Boolean = dao.byFingerprint(fp) != null

    suspend fun updateManual(q: QuestionEntity) {
        dao.update(q.copy(edited = true))
        adaylariUnut()
    }
    suspend fun delete(id: Long) {
        dao.delete(id)
        adaylariUnut()
    }
    suspend fun deleteAll() {
        dao.deleteAll()
        adaylariUnut()
    }
    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun allForExport() = dao.allForExport()

    /**
     * Bir kategorinin bütün kayıtlarını [yeni] ada taşır; kaç kayıt değişti.
     *
     * Aynı kategori arşivde farklı yazılışlarla da durabiliyor (elle
     * "din kültürü" yazılmış, yedekten "DİN KÜLTÜRÜ" gelmiş); hepsi taşınıyor.
     * Elle düzeltilmiş kayıtlar da dahil: kategorinin adı değişti, kaydın
     * içeriği değil.
     */
    suspend fun renameCategory(eski: String, yeni: String): Int {
        val k = KategoriListesi.anahtar(eski)
        if (k.isEmpty() || yeni.isBlank()) return 0
        return db.withTransaction {
            dao.distinctCategories()
                .filter { it != yeni && KategoriListesi.anahtar(it) == k }
                .sumOf { dao.renameCategory(it, yeni) }
        }
    }

    companion object {
        private const val TAG = "SoruArsivi/Repo"

        /**
         * Kayıttaki cevap korunsun mu, yoksa yeni gözlem üstüne yazsın mı?
         *
         * Zayıf bir okuma, daha sağlam bir gözlemle yazılmış cevabın üstüne
         * yazmamalı. Arşive bir kez yanlış cevap girdiğinde otomatik mod her
         * turda ona basmaya devam ettiği için hata kendini besliyor.
         *
         * Eşit güçte gözlem üstüne yazabiliyor: bozuk eski kayıtların yeni
         * karşılaşmalarda kendiliğinden düzelmesi buna bağlı.
         */
        internal fun shouldKeepStored(
            storedIndex: Int?,
            storedSource: String?,
            incoming: AnswerEvidence
        ): Boolean = storedIndex != null && incoming.strength < strengthOf(storedSource)

        /**
         * Eski şık işareti kuralının bozduğu bir kaydı tanır.
         *
         * O kural "1. Dönem"deki "1."i şık işareti sanıp siliyordu; arşivde
         * dört şıkkı da "Dönem" olan sorular bundan. Böyle bir kayıt yeniden
         * okunduğunda bulanık eşleşme onu buluyor ama şıklar yalnızca liste
         * kısaysa tamamlandığı için bozuk hâli kalıcıydı: şıklar ayırt
         * edilemediğinden doğru cevap da hiçbir zaman yazılamıyordu.
         *
         * Onarım dar tutuldu: kayıttaki şıklar, ekrandaki şıkların eski
         * kuraldan geçmiş hâliyle **birebir** aynı olmalı (sırası önemsiz)
         * ve ekrandaki şıklar birbirinden ayırt edilebilmeli.
         *
         * @return onarım gerekmiyorsa null.
         */
        internal fun siraSayisiOnarimi(
            stored: List<String>,
            storedCorrect: String?,
            fresh: List<String>
        ): Onarim? {
            if (stored.size != fresh.size || fresh.size < 2) return null
            val yeni = fresh.map { TurkishText.normalizeKey(it) }
            if (yeni.toSet().size != yeni.size) return null
            val eski = stored.map { TurkishText.normalizeKey(it) }.sorted()
            if (eski == yeni.sorted()) return null
            val eskiKuralla = fresh.map {
                TurkishText.normalizeKey(TurkishText.stripOptionPrefixLegacy(it))
            }
            if (eski != eskiKuralla.sorted()) return null

            // Kayıttaki cevabın metni eski kuraldan geçmiş hâliyle aranıyor;
            // "Dönem" dört şıkta birden geçtiği için orada cevap belirsiz
            // kalır ve boşaltılır — bir sonraki renk okuması yeniden öğretir.
            val dogru = storedCorrect?.let { TurkishText.normalizeKey(it) }
                ?: return Onarim(null)
            return Onarim(eskiKuralla.indices.filter { eskiKuralla[it] == dogru }.singleOrNull())
        }

        /**
         * Eski temizlik kuralının işaretini sildiği şıkları tanır.
         *
         * O kural sayının başındaki eksiyi süs sanıp kırpıyordu: "-16 / -4 /
         * 4 / 16" şıkları arşive "16 / 4 / 4 / 16" diye yazılmıştı. Şıklar
         * ayırt edilemediği için doğru cevap hiç yazılamıyordu ve parmak
         * izi de işareti görmediği için yeni okuma hep bu kayda düşüyordu.
         *
         * Onarım dar tutuldu: ekranda en az bir eksili şık olmalı, ekrandaki
         * şıklar birbirinden ayırt edilebilmeli ve kayıttaki şıklar ekrandakilerin
         * eksisi silinmiş hâliyle **birebir** aynı olmalı (sırası önemsiz).
         *
         * @return onarım gerekmiyorsa null.
         */
        internal fun isaretOnarimi(
            stored: List<String>,
            storedCorrect: String?,
            fresh: List<String>
        ): Onarim? {
            if (stored.size != fresh.size || fresh.size < 2) return null
            if (fresh.none { it.startsWith('-') }) return null
            val yeni = fresh.map { TurkishText.lower(it.trim()) }
            if (yeni.toSet().size != yeni.size) return null
            val eskiKuralla = fresh.map { TurkishText.lower(it.trim().trimStart('-').trim()) }
            val eski = stored.map { TurkishText.lower(it.trim()) }
            if (eski.sorted() != eskiKuralla.sorted() || eski.sorted() == yeni.sorted()) return null

            // Kayıttaki cevap "4" ise ekrandaki "-4" ile "4"ten hangisi olduğu
            // bilinmiyor: boşaltılıyor. Tek karşılığı varsa yeni sırasıyla.
            val dogru = storedCorrect?.let { TurkishText.lower(it.trim()) } ?: return Onarim(null)
            return Onarim(eskiKuralla.indices.filter { eskiKuralla[it] == dogru }.singleOrNull())
        }

        /**
         * İki kez okunmuş şıkları tanır: ML Kit bazı rakamları iki kez
         * döndürüyordu ve "6" şıkkı arşive "6 6" diye yazılıyordu (bkz.
         * `QuestionParser.kutuMetni`). Kayıttaki "6 6" ekrandaki "6" ile hiç
         * eşleşmediği için cevap ne öğrenilebiliyor ne kullanılabiliyordu.
         *
         * Dar: yalnızca harfsiz, kendini tekrarlayan şıklar ("6 6", "4 4")
         * tekine indiriliyor ve kayıttaki şıklar böylece ekrandakilerle
         * birebir aynı olmalı (sırası önemsiz).
         */
        internal fun ciftOkumaOnarimi(
            stored: List<String>,
            storedCorrect: String?,
            fresh: List<String>
        ): Onarim? {
            if (stored.size != fresh.size || fresh.size < 2) return null
            val yeni = fresh.map { TurkishText.lower(it.trim()) }
            if (yeni.toSet().size != yeni.size) return null
            val eski = stored.map { TurkishText.lower(it.trim()) }
            val tekli = eski.map { tekrarSil(it) }
            if (tekli == eski || tekli.sorted() != yeni.sorted()) return null
            val dogru = storedCorrect?.let { tekrarSil(TurkishText.lower(it.trim())) } ?: return Onarim(null)
            return Onarim(yeni.indices.filter { yeni[it] == dogru }.singleOrNull())
        }

        /** "6 6" → "6"; harf içeren ya da tekrarlamayan metne dokunmaz. */
        private fun tekrarSil(s: String): String {
            val parcalar = s.split(' ').filter { it.isNotEmpty() }
            if (parcalar.size < 2 || parcalar.any { p -> p.any { it.isLetter() } }) return s
            return if (parcalar.all { it == parcalar[0] }) parcalar[0] else s
        }

        /** Tarama günlüğündeki onarım adları. */
        const val ISARET_ONARIMI = "eksi işaretleri"
        const val SIRA_SAYISI_ONARIMI = "sıra sayıları"
        const val CIFT_OKUMA_ONARIMI = "iki kez okunmuş rakamlar"

        /** [SaveResult.Duplicate.yol] değerleri. */
        const val BENZERLIK = "benzerlik"
        const val PARMAK_IZI = "parmak izi"

        private fun strengthOf(source: String?): Int = when (source) {
            AnswerEvidence.CERTAIN.label -> AnswerEvidence.CERTAIN.strength
            AnswerEvidence.GREEN.label -> AnswerEvidence.GREEN.strength
            AnswerEvidence.TIMEOUT.label -> AnswerEvidence.TIMEOUT.strength
            AnswerEvidence.TOUCH.label -> AnswerEvidence.TOUCH.strength
            Importers.ANSWER_SOURCE -> AnswerEvidence.TOUCH.strength
            else -> 0
        }
        /**
         * Bulanık tekrar kontrolünün karşılaştırdığı kayıt sayısı.
         *
         * Tüm arşivi kapsayacak kadar büyük: pencere dar olduğunda OCR'ın bir
         * harfi yanlış okuduğu her soru ikinci bir kayıt açıyordu.
         */
        private const val DEDUP_POOL = 20_000
        @Volatile private var INSTANCE: Repo? = null
        fun get(context: Context): Repo =
            INSTANCE ?: synchronized(this) { INSTANCE ?: Repo(context).also { INSTANCE = it } }
    }
}
