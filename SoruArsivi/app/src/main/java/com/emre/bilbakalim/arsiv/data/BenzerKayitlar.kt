package com.emre.bilbakalim.arsiv.data

import android.content.Context
import com.emre.bilbakalim.arsiv.capture.YapayZeka
import com.emre.bilbakalim.arsiv.util.OtomatikYedek
import com.emre.bilbakalim.arsiv.util.TurkishText
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Aynı sorunun birden çok kaydını (OCR farkıyla ayrı kaydedilmiş:
 * "ldea" / "Idea", "SIZ /-siz" / "SIZ-siz") bulup birleştirme.
 *
 * Üç adım, ikisi bilerek temkinli:
 *  1. **Aday:** soru metni benzer **ve** şıkların en az üçü ortak. Şık
 *     şartı şart: bu oyunda aynı kelime farklı şık setleriyle de geliyor
 *     ("Concrete" bir sette Beton, ötekinde Somut) ve bunlar ayrı kalmalı,
 *     yoksa bir setin cevabı kaybolur.
 *  2. **Yapay zekâ:** adaylar Groq'a ve Gemini'ye "aynı soru mu" diye
 *     soruluyor; "Desert / Dessert", "North / South", "Good morning /
 *     Good evening" gibi şıkları aynı ama sorusu farklı çiftleri o eliyor.
 *  3. **Sen:** yapay zekânın aynı dediği çiftler listeleniyor; hangisinin
 *     tutulacağını sen seçiyorsun. Kendiliğinden hiçbir şey birleşmiyor.
 *
 * Birleştirme geri alınabilir: silinen kayıt ve tutulanın önceki hâli
 * dosyada saklanıyor. İlk birleştirmeden önce arşiv ayrıca yedekleniyor.
 */
class BenzerKayitlar private constructor(private val context: Context) {

    /** [groq] / [gemini]: true aynı, false farklı, null sorulmadı ya da cevap yok. */
    data class Cift(val a: QuestionEntity, val b: QuestionEntity, val groq: Boolean?, val gemini: Boolean?) {
        val ikisiDeAyni: Boolean get() = groq != false && gemini != false && (groq == true || gemini == true)
    }

    data class Durum(
        val calisiyor: Boolean = false,
        val aday: Int = 0,
        val sorulan: Int = 0,
        val farkliBulunan: Int = 0,
        val mesaj: String? = null
    )

    private val repo = Repo.get(context)
    private val prefs = Prefs.get(context)
    private val yz = YapayZeka({})
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var is_: Job? = null
    private val elenenDosya = File(context.filesDir, "benzer_kayit_elenen.txt")
    private val gecmisDosya = File(context.filesDir, "birlestirme_gecmisi.json")
    @Volatile private var yedeklendi = false

    private val _durum = MutableStateFlow(Durum())
    val durum: StateFlow<Durum> = _durum
    private val _ciftler = MutableStateFlow<List<Cift>>(emptyList())
    val ciftler: StateFlow<List<Cift>> = _ciftler
    private val _gecmis = MutableStateFlow(gecmisiOku())
    /** Geri alınabilecek birleştirme sayısı. */
    val geriAlinabilir: StateFlow<List<Gecmis>> = _gecmis

    fun bul(kategori: String?, etiketsiz: Boolean) {
        if (is_?.isActive == true) return
        is_ = scope.launch {
            try {
                calis(kategori, etiketsiz)
            } catch (e: CancellationException) {
                _durum.value = _durum.value.copy(calisiyor = false, mesaj = "Durduruldu.")
                throw e
            } catch (t: Throwable) {
                _durum.value = _durum.value.copy(calisiyor = false, mesaj = "Hata: ${t.message}")
            }
        }
    }

    fun durdur() { is_?.cancel() }

    private suspend fun calis(kategori: String?, etiketsiz: Boolean) {
        val s = prefs.state.value
        val groq = YapayZeka.anahtarSirasi(s.groqKeys, emptyList())
        val gemini = YapayZeka.anahtarSirasi(emptyList(), s.geminiKeys)
        if (groq.isEmpty() && gemini.isEmpty()) {
            _durum.value = Durum(mesaj = "Yapay zekâ anahtarı girilmemiş (Ayarlar).")
            return
        }
        _ciftler.value = emptyList()
        _durum.value = Durum(calisiyor = true, mesaj = "Adaylar aranıyor…")
        val satirlar = repo.kontrolAdaylari(kategori, etiketsiz, 0).filter { it.options.size >= 2 }
        val elenen = elenenleriOku()
        val adaylar = adaylar(satirlar.map { Satir(it.id, it.questionText, it.options) })
            .filter { (a, b) -> ciftAnahtari(a, b) !in elenen }
        val kayit = satirlar.associateBy { it.id }
        _durum.value = _durum.value.copy(aday = adaylar.size,
            mesaj = if (adaylar.isEmpty()) "Benzer kayıt adayı yok." else "Yapay zekâya soruluyor…")
        if (adaylar.isEmpty()) {
            _durum.value = _durum.value.copy(calisiyor = false)
            return
        }

        for (parti in adaylar.chunked(PARTI)) {
            val ciftler = parti.map { (a, b) -> kayit.getValue(a) to kayit.getValue(b) }
            val istem = ciftIstemi(ciftler.map { (a, b) -> (a.questionText to a.options) to (b.questionText to b.options) })
            val (g, m) = coroutineScope {
                val x = async { if (groq.isEmpty()) null else sor(groq, "Groq", istem, ciftler.size) }
                val y = async { if (gemini.isEmpty()) null else sor(gemini, "Gemini", istem, ciftler.size) }
                x.await() to y.await()
            }
            if ((groq.isNotEmpty() && g == null) || (gemini.isNotEmpty() && m == null)) {
                _durum.value = _durum.value.copy(calisiyor = false)
                return
            }
            var farkli = 0
            val yeni = ArrayList<Cift>()
            ciftler.forEachIndexed { i, (a, b) ->
                val c = Cift(a, b, g?.getOrNull(i), m?.getOrNull(i))
                // İkisi de farklı dediyse (ya da tek sağlayıcı farklı dediyse)
                // gösterilmiyor ve bir daha sorulmuyor.
                if (c.groq != true && c.gemini != true && (c.groq == false || c.gemini == false)) {
                    farkli++
                    elenenEkle(ciftAnahtari(a.id, b.id))
                } else if (c.groq != null || c.gemini != null) {
                    yeni += c
                }
            }
            _ciftler.value = _ciftler.value + yeni
            val d = _durum.value
            _durum.value = d.copy(sorulan = d.sorulan + parti.size, farkliBulunan = d.farkliBulunan + farkli)
        }
        val d = _durum.value
        _durum.value = d.copy(
            calisiyor = false,
            mesaj = "Bitti: ${d.aday} adaydan ${d.farkliBulunan} çift farklı soru çıktı, " +
                "${_ciftler.value.size} çift senin kararını bekliyor."
        )
    }

    private suspend fun sor(anahtarlar: List<YapayZeka.Anahtar>, ad: String, istem: String, n: Int): List<Boolean?>? =
        yz.donerekSor(
            anahtarlar, ad,
            bildir = { _durum.value = _durum.value.copy(mesaj = it) },
            dene = { a, model -> yz.jsonSor(a, model, CIFT_SISTEM, istem) },
            coz = { metin -> ciftCevabi(metin, n).takeIf { l -> l.any { it != null } } }
        )

    /** [tutulan] kalır, öteki silinir. Başarılıysa null, değilse sebebi. */
    fun birlestir(c: Cift, tutulan: Long, sonuc: (String?) -> Unit) {
        scope.launch {
            val (tut, sil) = if (tutulan == c.a.id) c.a to c.b else c.b to c.a
            if (!yedeklendi) {
                yedeklendi = OtomatikYedek.yedekle(context, ek = "birlestirme_oncesi").isSuccess
            }
            val g = repo.birlestir(tut.id, sil.id)
            if (g == null) {
                sonuc("Kayıtlar bu arada değişmiş ya da silinmiş; liste yenilendi.")
            } else {
                synchronized(this@BenzerKayitlar) {
                    _gecmis.value = (_gecmis.value + Gecmis(g.first, g.second)).takeLast(GECMIS_SINIRI)
                    gecmisiYaz()
                }
                sonuc(null)
            }
            // Silinen kayıt başka çiftlerde de olabilir.
            _ciftler.value = _ciftler.value.filterNot {
                it === c || it.a.id == sil.id || it.b.id == sil.id
            }
        }
    }

    /** "Ayrı sorular": listeden çıkar, bir daha sorma. */
    fun ayriBirak(c: Cift) {
        elenenEkle(ciftAnahtari(c.a.id, c.b.id))
        _ciftler.value = _ciftler.value.filterNot { it === c }
    }

    /** Son birleştirmeyi geri alır. */
    fun geriAl(sonuc: (String) -> Unit) {
        scope.launch {
            val son = synchronized(this@BenzerKayitlar) { _gecmis.value.lastOrNull() } ?: return@launch
            val ok = repo.birlestirmeyiGeriAl(son.tutulanOnce, son.silinen)
            synchronized(this@BenzerKayitlar) {
                _gecmis.value = _gecmis.value.dropLast(1)
                gecmisiYaz()
            }
            sonuc(
                if (ok) "Geri alındı: «${son.silinen.questionText.take(40)}» yeniden arşivde."
                else "Geri alınamadı: kayıt bu arada değişmiş."
            )
        }
    }

    /** Elenen çiftleri unut: hepsi yeniden sorulsun. */
    fun eleneniUnut() {
        runCatching { elenenDosya.delete() }
    }

    // --- dosyalar --------------------------------------------------------

    data class Gecmis(val tutulanOnce: QuestionEntity, val silinen: QuestionEntity)

    private fun elenenleriOku(): Set<String> =
        runCatching { if (elenenDosya.exists()) elenenDosya.readLines().toSet() else emptySet() }
            .getOrDefault(emptySet())

    @Synchronized
    private fun elenenEkle(k: String) {
        runCatching { elenenDosya.appendText(k + "\n") }
    }

    private fun gecmisiYaz() {
        runCatching {
            gecmisDosya.writeText(JSONArray().apply {
                _gecmis.value.forEach {
                    put(JSONObject().put("tutulan", kayitJson(it.tutulanOnce)).put("silinen", kayitJson(it.silinen)))
                }
            }.toString())
        }
    }

    private fun gecmisiOku(): List<Gecmis> = runCatching {
        if (!gecmisDosya.exists()) return@runCatching emptyList()
        val a = JSONArray(gecmisDosya.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Gecmis(kayitOku(o.getJSONObject("tutulan")), kayitOku(o.getJSONObject("silinen")))
        }
    }.getOrDefault(emptyList())

    /** Aday bulma için gereken en az alan. */
    data class Satir(val id: Long, val soru: String, val siklar: List<String>)

    companion object {
        private const val PARTI = 20
        private const val GECMIS_SINIRI = 50
        /** Bundan çok soruda geçen şık ("Evet", "Doğu") aday üretmekte kullanılmıyor. */
        private const val YAYGIN_SIK = 300
        /** Soru metni benzerliği alt sınırı (şıkların üçü ortaksa). */
        private const val SORU_BENZERLIGI = 0.5f

        internal fun ciftAnahtari(a: Long, b: Long) = "${minOf(a, b)}-${maxOf(a, b)}"

        /**
         * Aday çiftler: şıkların en az üçü ortak (OCR farkı payıyla) ve soru
         * metni benzer. Önce birebir aynı şık anahtarlarıyla kaba eleme
         * (2000 kayıtta 2 milyon çifti tek tek karşılaştırmamak için).
         */
        internal fun adaylar(satirlar: List<Satir>): List<Pair<Long, Long>> {
            val anahtarlar = satirlar.map { s -> s.siklar.map { TurkishText.normalizeKey(it) } }
            val dizin = HashMap<String, MutableList<Int>>()
            anahtarlar.forEachIndexed { i, l -> l.toSet().filter { it.isNotEmpty() }.forEach { dizin.getOrPut(it) { ArrayList() } += i } }
            val ortak = HashMap<Long, Int>()
            for (l in dizin.values) {
                if (l.size > YAYGIN_SIK) continue
                for (x in l.indices) for (y in x + 1 until l.size) {
                    val k = l[x].toLong() shl 32 or l[y].toLong()
                    ortak[k] = (ortak[k] ?: 0) + 1
                }
            }
            val sonuc = ArrayList<Pair<Long, Long>>()
            for ((k, n) in ortak) {
                if (n < 2) continue
                val i = (k ushr 32).toInt()
                val j = (k and 0xffffffffL).toInt()
                val a = satirlar[i]
                val b = satirlar[j]
                if (TurkishText.optionKeyOverlap(a.siklar, b.siklar) < minOf(3, a.siklar.size, b.siklar.size)) continue
                val benzerlik = TurkishText.similarityOfKeys(
                    TurkishText.normalizeKey(a.soru), TurkishText.normalizeKey(b.soru)
                )
                if (benzerlik < SORU_BENZERLIGI) continue
                sonuc += minOf(a.id, b.id) to maxOf(a.id, b.id)
            }
            return sonuc.sortedWith(compareBy({ it.first }, { it.second }))
        }

        internal val CIFT_SISTEM: String =
            "You review a quiz archive for duplicate records. Each numbered item shows two records, " +
                "A and B, each a question with its answer options, read from a phone screen by OCR. " +
                "Answer SAME if they are the same quiz question with the same options and differ only " +
                "by OCR or typing noise (letter case, spacing, punctuation, a misread or dropped " +
                "character, option order). Answer DIFFERENT if they ask something different: a " +
                "different word or meaning (e.g. 'Desert' vs 'Dessert', 'North' vs 'South', " +
                "'Good morning' vs 'Good evening'), a different number, negation ('is' vs 'is not'), " +
                "or options that differ in meaning. When unsure, answer DIFFERENT. Reply ONLY with a " +
                "JSON object mapping each item number to \"SAME\" or \"DIFFERENT\", e.g. " +
                "{\"1\":\"SAME\",\"2\":\"DIFFERENT\"}."

        internal fun ciftIstemi(
            ciftler: List<Pair<Pair<String, List<String>>, Pair<String, List<String>>>>
        ): String = ciftler.withIndex().joinToString("\n") { (i, c) ->
            fun yaz(k: Pair<String, List<String>>) =
                "\"" + k.first.trim().replace('\n', ' ') + "\" [" + k.second.joinToString(" | ") { it.trim() } + "]"
            "${i + 1}. A: ${yaz(c.first)} — B: ${yaz(c.second)}"
        }

        /** {"1":"SAME",...} → true (aynı) / false (farklı) / null. */
        internal fun ciftCevabi(metin: String, n: Int): List<Boolean?> {
            val j = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(metin)?.value
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return List(n) { null }
            return List(n) { i ->
                when (j.optString((i + 1).toString()).trim().uppercase().firstOrNull()) {
                    'S', 'A' -> true   // SAME / AYNI
                    'D', 'F' -> false  // DIFFERENT / FARKLI
                    else -> null
                }
            }
        }

        /** Birleştirmeyi tutulan kayda uygular (silinen ayrıca silinir). */
        internal fun birlesmis(tut: QuestionEntity, sil: QuestionEntity): QuestionEntity {
            // Tutulanın cevabı yoksa ötekinin cevabı metniyle taşınır.
            val cevap = tut.correctIndex
                ?: sil.correctText?.let { TurkishText.matchIndex(tut.options, it) }
            return tut.copy(
                correctIndex = cevap,
                answerSource = if (tut.correctIndex == null && cevap != null) sil.answerSource else tut.answerSource,
                category = tut.category?.takeIf { it.isNotBlank() } ?: sil.category,
                note = tut.note?.takeIf { it.isNotBlank() } ?: sil.note,
                screenshotPath = tut.screenshotPath ?: sil.screenshotPath,
                capturedAt = minOf(tut.capturedAt, sil.capturedAt),
                // İki kayıt ayrı karşılaşmaları sayıyordu: toplanıyor.
                seenCount = tut.seenCount + sil.seenCount,
                answeredCount = tut.answeredCount + sil.answeredCount,
                correctCount = tut.correctCount + sil.correctCount,
                edited = tut.edited || sil.edited
            )
        }

        internal fun kayitJson(q: QuestionEntity): JSONObject = JSONObject().apply {
            put("id", q.id); put("questionText", q.questionText)
            put("optionA", q.optionA ?: JSONObject.NULL); put("optionB", q.optionB ?: JSONObject.NULL)
            put("optionC", q.optionC ?: JSONObject.NULL); put("optionD", q.optionD ?: JSONObject.NULL)
            put("correctIndex", q.correctIndex ?: JSONObject.NULL)
            put("answerSource", q.answerSource ?: JSONObject.NULL)
            put("category", q.category ?: JSONObject.NULL)
            put("source", q.source); put("confidence", q.confidence.toDouble())
            put("fingerprint", q.fingerprint)
            put("screenshotPath", q.screenshotPath ?: JSONObject.NULL)
            put("capturedAt", q.capturedAt); put("seenCount", q.seenCount)
            put("answeredCount", q.answeredCount); put("correctCount", q.correctCount)
            put("edited", q.edited)
            put("note", q.note ?: JSONObject.NULL)
            put("optionSigs", q.optionSigs ?: JSONObject.NULL)
        }

        internal fun kayitOku(o: JSONObject): QuestionEntity {
            fun s(k: String) = if (o.isNull(k)) null else o.getString(k)
            return QuestionEntity(
                id = o.getLong("id"), questionText = o.getString("questionText"),
                optionA = s("optionA"), optionB = s("optionB"), optionC = s("optionC"), optionD = s("optionD"),
                correctIndex = if (o.isNull("correctIndex")) null else o.getInt("correctIndex"),
                answerSource = s("answerSource"), category = s("category"),
                source = o.getString("source"), confidence = o.getDouble("confidence").toFloat(),
                fingerprint = o.getString("fingerprint"), screenshotPath = s("screenshotPath"),
                capturedAt = o.getLong("capturedAt"), seenCount = o.getInt("seenCount"),
                answeredCount = o.getInt("answeredCount"), correctCount = o.getInt("correctCount"),
                edited = o.getBoolean("edited"), note = s("note"), optionSigs = s("optionSigs")
            )
        }

        @Volatile private var INSTANCE: BenzerKayitlar? = null
        fun get(context: Context): BenzerKayitlar =
            INSTANCE ?: synchronized(this) { INSTANCE ?: BenzerKayitlar(context.applicationContext).also { INSTANCE = it } }
    }
}
