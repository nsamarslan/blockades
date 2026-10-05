package com.emre.bilbakalim.arsiv.capture

import android.os.SystemClock
import com.emre.bilbakalim.arsiv.data.AnahtarKotasi
import com.emre.bilbakalim.arsiv.util.TurkishText
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cevabı arşivde olmayan soruyu bir dil modeline sorar.
 *
 * Otomatik mod bilmediği soruda rastgele basmak yerine bunu kullanıyor.
 * İki ücretsiz sağlayıcı var, ikisi de kullanıcının kendi anahtarıyla:
 *
 *  - **Groq** (groq.com, anahtar `gsk_` ile başlar) — çok hızlı, ~0,4 sn.
 *  - **Gemini** (Google AI Studio) — biraz daha yavaş, ~0,7 sn.
 *
 * Her sağlayıcıya birden çok anahtar girilebiliyor ve anahtarlar
 * **dönüşümlü** kullanılıyor: Groq 1, Gemini 1, Groq 2, Gemini 2, ...
 * Ücretsiz kotalar dakikalık (Groq: model başına dakikada
 * 8.000 token, yani ~20 soru) ve günlük (Groq: model başına günde 1.000
 * istek) tutulduğu için yükü bölmek ikisinin de dakikalık sınırına
 * takılmayı önlüyor ve günlük kotaları eşit eritiyor.
 *
 * Sıradaki hata verirse, kotası dolarsa (HTTP 429) ya da süre aşılırsa
 * aynı soru ötekine soruluyor. Kotası dolan model bir süre hiç
 * denenmiyor; Groq kalan kotayı yanıt başlıklarında da bildirdiği için
 * kota bitmeden önce de kenara alınabiliyor. Böylece her soruda boşuna
 * bekleyip oyunun süresini yemiyoruz.
 *
 * İstem modelden yalnızca tek bir harf istiyor (A, B, C, D); dönen metin
 * [harfiCoz] ile şık sırasına çevriliyor. Model ne derse desin bot yine
 * ekrandaki şıklardan birine basıyor — cevap çözülemezse null dönüyor ve
 * çağıran taraf rastgeleye düşüyor.
 */
class YapayZeka(
    private val log: (String) -> Unit,
    /**
     * Kota cezası verilmiş anahtarları da dene. Yalnızca ayarlardaki
     * "Anahtarları dene" düğmesi için: kullanıcı anahtarı düzelttiyse
     * eski ceza denemeyi atlatmasın.
     */
    private val cezayiYoksay: Boolean = false
) {

    enum class Saglayici(val ad: String) { GROQ("Groq"), GEMINI("Gemini") }

    /** Bir anahtar: hangi sağlayıcının kaçıncısı ([no] 1'den başlar). */
    data class Anahtar(val saglayici: Saglayici, val no: Int, val deger: String) {
        /** Günlükte görünen ad: "Groq 2". Anahtarın kendisi yazılmıyor. */
        val ad: String get() = "${saglayici.ad} $no"
        override fun toString() = ad
    }

    /** Bir sorunun sonucu. [index] null ise hiçbir anahtar cevap veremedi. */
    data class Sonuc(
        val index: Int?,
        val anahtar: Anahtar?,
        val model: String?,
        val sureMs: Long,
        /** Denenip başarısız olanlar, günlük için ("Groq: kota doldu"). */
        val hatalar: List<String>,
        /** Modelin kendi söylediği güven (0-100); yazmadıysa null. */
        val guven: Int? = null
    )

    private val bekle get() = BEKLE

    /** Dönüşüm sayacı: her soruda bir sonraki anahtardan başlanıyor. */
    @Volatile private var tur = 0

    suspend fun sor(
        soru: String,
        siklar: List<String>,
        groqAnahtarlari: List<String>,
        geminiAnahtarlari: List<String>
    ): Sonuc = withContext(Dispatchers.IO) {
        val bas = SystemClock.elapsedRealtime()
        val hatalar = ArrayList<String>()
        val sira = siralama(anahtarSirasi(groqAnahtarlari, geminiAnahtarlari), tur++)
        // Reddedilen anahtarın öteki modelleri bu soruda atlanıyor.
        val reddedilen = HashSet<Anahtar>()

        for ((a, model) in sira) {
            if (a in reddedilen) continue
            val sag = a.saglayici
            val anahtar = a.deger
            val gecen = SystemClock.elapsedRealtime() - bas
            if (gecen > TOPLAM_BUTCE_MS) {
                hatalar += "süre doldu"
                return@withContext Sonuc(null, null, null, gecen, hatalar)
            }
            val k = bekleAnahtari(a, model)
            val simdi = SystemClock.elapsedRealtime()
            val kadar = if (cezayiYoksay) 0L else synchronized(bekle) { bekle[k] ?: 0L }
            if (simdi < kadar) {
                hatalar += "${a.ad} $model: kota/anahtar sorunu, ${(kadar - simdi) / 1000} sn atlanıyor"
                continue
            }
            // Süre sınırını bağlantının kendi zaman aşımları koyuyor.
            val yanit = runCatching { istek(sag, model, anahtar, soru, siklar) }
                .getOrElse { Yanit(-1, it.message ?: it.javaClass.simpleName) }

            // Kota bitmek üzereyse (Groq başlıkları) model şimdiden
            // kenara alınıyor: sıradaki soru boşuna 429 yemesin.
            if (yanit.onlemMs > 0) {
                synchronized(bekle) { bekle[k] = SystemClock.elapsedRealtime() + yanit.onlemMs }
            }
            if (yanit.kod == 200) {
                val metin = runCatching { cevapMetni(sag, yanit.govde) }.getOrNull()
                val index = metin?.let { harfiCoz(it, siklar) }
                if (index != null) {
                    return@withContext Sonuc(
                        index, a, model, SystemClock.elapsedRealtime() - bas, hatalar,
                        guvenCoz(metin)
                    )
                }
                hatalar += "${a.ad} $model: anlaşılmayan cevap «${metin?.take(40) ?: yanit.govde.take(80)}»"
                continue
            }

            val ceza = cezaSuresiMs(yanit.kod, yanit.govde)
            if (ceza > 0) synchronized(bekle) { bekle[k] = maxOf(bekle[k] ?: 0L, SystemClock.elapsedRealtime() + ceza) }
            hatalar += "${a.ad} $model: ${hataOzeti(yanit.kod, yanit.govde)}"
            // Anahtar geçersizse aynı anahtarla öteki modeli denemek
            // boşuna.
            if (yanit.kod == 401 || yanit.kod == 403) {
                reddedilen += a
                modeller(sag).forEach { m ->
                    synchronized(bekle) { bekle[bekleAnahtari(a, m)] = SystemClock.elapsedRealtime() + ceza }
                }
            }
        }
        Sonuc(null, null, null, SystemClock.elapsedRealtime() - bas, hatalar)
    }

    /** Anahtar değişince eski cezalar unutulsun. */
    fun sifirla() = synchronized(bekle) { bekle.clear() }

    /** [onlemMs]: kota bitmek üzere olduğu için modelin kenara alınacağı süre. */
    private class Yanit(val kod: Int, val govde: String, val onlemMs: Long = 0L)

    private fun istek(
        sag: Saglayici, model: String, anahtar: String, soru: String, siklar: List<String>
    ): Yanit = gonder(
        sag, model, anahtar,
        when (sag) {
            Saglayici.GROQ -> groqGovdesi(model, soru, siklar)
            Saglayici.GEMINI -> geminiGovdesi(model, soru, siklar)
        },
        ISTEK_SURESI_MS
    )

    /** Toplu sorunun sonucu (bkz. [topluSor]). */
    sealed interface Toplu {
        /** Soru sırasıyla şık sıraları; anlaşılmayanlar null. */
        data class Tamam(val cevaplar: List<Int?>) : Toplu
        /** Bu anahtar/model şimdilik kullanılamaz (kota); [ms] sonra dene. */
        data class Bekle(val ms: Long, val neden: String) : Toplu
        /** Geçici hata (ağ, 5xx): biraz sonra yeniden denenebilir. */
        data class Hata(val neden: String) : Toplu
    }

    /** Bu anahtar/model kaç ms daha ceza beklemede (0: hazır). */
    fun bekleme(a: Anahtar, model: String): Long = kalanCeza(a, model)

    /**
     * Birçok soruyu tek istekte sorar (arşivi toplu kontrol için). Kota
     * cezaları oyunla ortak: biri doluysa öteki de bilir.
     */
    suspend fun topluSor(
        a: Anahtar,
        model: String,
        sorular: List<Pair<String, List<String>>>
    ): Toplu = withContext(Dispatchers.IO) {
        val k = bekleAnahtari(a, model)
        bekleme(a, model).takeIf { it > 0 }?.let { return@withContext Toplu.Bekle(it, "kota bekleniyor") }
        val govde = when (a.saglayici) {
            Saglayici.GROQ -> topluGroqGovdesi(model, sorular)
            Saglayici.GEMINI -> topluGeminiGovdesi(model, sorular)
        }
        val yanit = runCatching { gonder(a.saglayici, model, a.deger, govde, TOPLU_SURE_MS) }
            .getOrElse { Yanit(-1, it.message ?: it.javaClass.simpleName) }
        if (yanit.onlemMs > 0) synchronized(bekle) { bekle[k] = SystemClock.elapsedRealtime() + yanit.onlemMs }
        if (yanit.kod == 200) {
            val metin = runCatching { cevapMetni(a.saglayici, yanit.govde) }.getOrNull()
                ?: return@withContext Toplu.Hata("boş cevap")
            return@withContext Toplu.Tamam(topluCoz(metin, sorular.map { it.second.size }))
        }
        val ceza = cezaSuresiMs(yanit.kod, yanit.govde)
        if (ceza > 0) {
            synchronized(bekle) { bekle[k] = maxOf(bekle[k] ?: 0L, SystemClock.elapsedRealtime() + ceza) }
            return@withContext Toplu.Bekle(ceza, hataOzeti(yanit.kod, yanit.govde))
        }
        Toplu.Hata(hataOzeti(yanit.kod, yanit.govde))
    }

    private fun gonder(
        sag: Saglayici, model: String, anahtar: String, govde: String, okumaSuresi: Int
    ): Yanit {
        val url = when (sag) {
            Saglayici.GROQ -> GROQ_URL
            Saglayici.GEMINI -> "$GEMINI_URL${URLEncoder.encode(model, "UTF-8")}:generateContent"
        }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = BAGLANTI_SURESI_MS
            c.readTimeout = okumaSuresi
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Groq, Cloudflare arkasında: kimliksiz istemcileri "error code:
            // 1010" ile geri çeviriyor.
            c.setRequestProperty("User-Agent", "SoruArsivi/1.0 (Android)")
            when (sag) {
                Saglayici.GROQ -> c.setRequestProperty("Authorization", "Bearer $anahtar")
                Saglayici.GEMINI -> c.setRequestProperty("x-goog-api-key", anahtar)
            }
            c.outputStream.use { it.write(govde.toByteArray(Charsets.UTF_8)) }
            val kod = c.responseCode
            val akis = if (kod in 200..299) c.inputStream else c.errorStream
            val metin = akis?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            AnahtarKotasi.kaydet(
                sag.ad, anahtar, kod,
                if (kod == 200) null else hataOzeti(kod, metin),
                c.getHeaderField("x-ratelimit-remaining-requests")?.trim()?.toIntOrNull(),
                c.getHeaderField("x-ratelimit-limit-requests")?.trim()?.toIntOrNull()
            )
            val bekleSn = c.getHeaderField("retry-after")?.trim()?.toLongOrNull()
            val onlem = onlemSuresiMs(
                c.getHeaderField("x-ratelimit-remaining-requests"),
                c.getHeaderField("x-ratelimit-reset-requests"),
                c.getHeaderField("x-ratelimit-remaining-tokens"),
                c.getHeaderField("x-ratelimit-reset-tokens")
            )
            return Yanit(kod, if (bekleSn != null && kod == 429) "$metin\nretry-after=$bekleSn" else metin, onlem)
        } catch (e: IOException) {
            AnahtarKotasi.kaydet(sag.ad, anahtar, -1, "bağlantı: ${e.message ?: e.javaClass.simpleName}", null, null)
            return Yanit(-1, e.message ?: e.javaClass.simpleName)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /**
         * Anahtar/model bazında "şu ana kadar deneme" (elapsedRealtime).
         * Bütün istemcilerde ortak: oyundaki bot, uzak mod ve toplu kontrol
         * aynı kotayı kullanıyor, biri 429 aldıysa öteki de beklesin.
         */
        private val BEKLE = HashMap<String, Long>()

        /** Bu anahtar/model kaç ms daha ceza beklemede (0: hazır). Ayarlar ekranı için. */
        fun kalanCeza(a: Anahtar, model: String): Long {
            val kadar = synchronized(BEKLE) { BEKLE[bekleAnahtari(a, model)] ?: 0L }
            return (kadar - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

        /** Toplu istekte bir partideki soru sayısı. */
        const val TOPLU_PARTI = 25
        /** Toplu isteğin okuma süresi: 25 soru düşünmek uzun sürebiliyor. */
        private const val TOPLU_SURE_MS = 90_000

        internal val TOPLU_SISTEM: String =
            "You are an expert quiz solver for a Turkish trivia game. You get numbered " +
                "questions, each with lettered options (A-D); exactly one option is correct. " +
                "Option texts are read from the screen by OCR and may contain small typos. " +
                "Always pick the most likely option, never skip. Answer with ONLY a JSON " +
                "object mapping each question number to its letter, e.g. {\"1\":\"B\",\"2\":\"D\"}. " +
                "No explanation."

        internal fun topluIstem(sorular: List<Pair<String, List<String>>>): String =
            sorular.withIndex().joinToString("\n") { (i, s) ->
                "${i + 1}. " + s.first.trim().replace('\n', ' ') + " — " +
                    s.second.withIndex().joinToString(" | ") { (j, o) ->
                        "${'A' + j}) ${o.trim().replace('\n', ' ')}"
                    }
            }

        internal fun topluGroqGovdesi(model: String, sorular: List<Pair<String, List<String>>>): String =
            JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", TOPLU_SISTEM))
                    put(JSONObject().put("role", "user").put("content", topluIstem(sorular)))
                })
                put("temperature", 0)
                put("max_completion_tokens", 6000)
                put("reasoning_effort", "low")
                put("response_format", JSONObject().put("type", "json_object"))
            }.toString()

        internal fun topluGeminiGovdesi(model: String, sorular: List<Pair<String, List<String>>>): String =
            JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", TOPLU_SISTEM))))
                put("contents", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", topluIstem(sorular))))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0)
                    put("responseMimeType", "application/json")
                    put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
                })
            }.toString()

        /**
         * Toplu cevabı ({"1":"B",...}) şık sıralarına çevirir. [sikSayilari]
         * soru sırasıyla şık sayıları; anlaşılmayan ya da şık sayısını aşan
         * cevap null.
         */
        internal fun topluCoz(metin: String, sikSayilari: List<Int>): List<Int?> {
            val j = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(metin)?.value
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return sikSayilari.map { null }
            return sikSayilari.mapIndexed { i, n ->
                val h = j.optString((i + 1).toString()).trim().trimStart('(').firstOrNull()?.uppercaseChar()
                    ?: return@mapIndexed null
                (h - 'A').takeIf { it in 0 until n }
            }
        }

        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

        /**
         * Denenecek modeller, sırayla. Ücretsiz kotalar model başına
         * tutulduğu için birinin kotası dolunca öteki hâlâ cevap verebiliyor.
         */
        internal fun modeller(sag: Saglayici): List<String> = when (sag) {
            Saglayici.GROQ -> listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b")
            Saglayici.GEMINI -> listOf("gemini-3.5-flash-lite", "gemini-flash-lite-latest")
        }

        /** Bekleme tablosunun anahtarı; anahtarın kendisi değil özeti tutuluyor. */
        internal fun bekleAnahtari(a: Anahtar, model: String) =
            "${a.saglayici.name}/${a.deger.hashCode()}/$model"

        /**
         * Girilen anahtarları dönüşüm sırasına dizer: Groq 1, Gemini 1,
         * Groq 2, Gemini 2, ... Sayılar eşit değilse fazlası sona kalır
         * (Groq 3, Groq 4). Boşlar ve aynı anahtarın tekrarı atlanıyor;
         * numara ayar ekranındaki alanın numarası.
         */
        internal fun anahtarSirasi(groq: List<String>, gemini: List<String>): List<Anahtar> {
            fun diz(sag: Saglayici, l: List<String>) =
                l.mapIndexed { i, d -> Anahtar(sag, i + 1, d.trim()) }
                    .filter { it.deger.isNotEmpty() }
                    .distinctBy { it.deger }
            val g = diz(Saglayici.GROQ, groq)
            val m = diz(Saglayici.GEMINI, gemini)
            return (0 until maxOf(g.size, m.size)).flatMap { i -> listOfNotNull(g.getOrNull(i), m.getOrNull(i)) }
        }

        /**
         * Bir sorunun deneme sırası: önce her anahtarın asıl modeli
         * (anahtarlar [tur]'a göre dönüşümlü), sonra yedek modeller.
         * Groq 1 cevap veremezse önce Gemini 1'e soruluyor, Groq'un küçük
         * modeline değil.
         */
        internal fun siralama(anahtarlar: List<Anahtar>, tur: Int): List<Pair<Anahtar, String>> {
            if (anahtarlar.isEmpty()) return emptyList()
            val bas = Math.floorMod(tur, anahtarlar.size)
            val donmus = anahtarlar.drop(bas) + anahtarlar.take(bas)
            val enCok = donmus.maxOf { modeller(it.saglayici).size }
            return (0 until enCok).flatMap { i ->
                donmus.mapNotNull { a -> modeller(a.saglayici).getOrNull(i)?.let { a to it } }
            }
        }

        /**
         * Groq'un kota başlıklarından: kota bitmek üzereyse modelin ne
         * kadar kenara alınacağı (ms), değilse 0. Gemini bu başlıkları
         * göndermiyor; onda 429 gelince [cezaSuresiMs] devreye giriyor.
         */
        internal fun onlemSuresiMs(
            kalanIstek: String?, istekSifirlanma: String?,
            kalanToken: String?, tokenSifirlanma: String?
        ): Long {
            var ms = 0L
            if (kalanIstek?.trim()?.toLongOrNull()?.let { it <= 0 } == true) {
                ms = maxOf(ms, sureCoz(istekSifirlanma) ?: 60 * 60_000L)
            }
            if (kalanToken?.trim()?.toLongOrNull()?.let { it < TOKEN_ESIGI } == true) {
                ms = maxOf(ms, sureCoz(tokenSifirlanma) ?: 60_000L)
            }
            return ms
        }

        /** Groq süre biçimi: "2.1s", "1m26.4s", "7.66ms", "1h2m3s". */
        internal fun sureCoz(s: String?): Long? {
            val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parca = Regex("([0-9.]+)(ms|h|m|s)").findAll(t).toList()
            if (parca.isEmpty() || parca.joinToString("") { it.value } != t) return null
            return parca.sumOf { p ->
                val v = p.groupValues[1].toDoubleOrNull() ?: return null
                when (p.groupValues[2]) {
                    "h" -> v * 3_600_000
                    "m" -> v * 60_000
                    "s" -> v * 1000
                    else -> v
                }
            }.toLong()
        }

        /**
         * Bir soru ~300-400 token tutuyor; dakikalık token kotasında bundan
         * az kaldıysa sıradaki istek büyük ihtimalle 429 alır.
         */
        private const val TOKEN_ESIGI = 600L

        private const val BAGLANTI_SURESI_MS = 3000
        /** Tek isteğin süresi. Oyunun soru süresi kısa; uzun bekleyemeyiz. */
        private const val ISTEK_SURESI_MS = 5000
        /** Bütün denemelerin toplam süresi. */
        private const val TOPLAM_BUTCE_MS = 9000L

        internal fun harfler(n: Int): List<Char> = (0 until n).map { 'A' + it }

        internal val SISTEM_ISTEMI: String =
            "Sen Türkçe bir bilgi yarışmasında yarışan uzman bir oyuncusun. " +
                "Sana bir soru ve harflerle işaretlenmiş şıklar verilecek; " +
                "şıklardan yalnızca biri doğru. Şık metinleri ekrandan karakter " +
                "tanımayla okunduğu için küçük yazım hataları olabilir. " +
                "Emin olmasan bile en olası şıkkı seç; asla boş bırakma. " +
                "Yanıtın YALNIZCA şu biçimde olsun: doğru şıkkın büyük harfi, bir " +
                "boşluk ve cevabından ne kadar emin olduğun (0-100 arası bir sayı). " +
                "Örnek: B 85. Tahmin ediyorsan düşük, kesin biliyorsan yüksek sayı " +
                "ver. Açıklama, şık metni ya da başka hiçbir şey yazma."

        internal fun kullaniciIstemi(soru: String, siklar: List<String>): String {
            val h = harfler(siklar.size)
            return buildString {
                append("Soru: ").append(soru.trim().replace('\n', ' ')).append("\n\n")
                siklar.forEachIndexed { i, s ->
                    append(h[i]).append(") ").append(s.trim().replace('\n', ' ')).append('\n')
                }
                append("\nYanıt biçimi: ").append(h.joinToString("/"))
                append(" harflerinden biri ve güven, örnek: ").append(h[1]).append(" 70")
            }
        }

        internal fun groqGovdesi(model: String, soru: String, siklar: List<String>): String =
            JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SISTEM_ISTEMI))
                    put(JSONObject().put("role", "user").put("content", kullaniciIstemi(soru, siklar)))
                })
                put("temperature", 0)
                // gpt-oss düşünen bir model: düşünme de bu sınıra sayılıyor,
                // dar tutulursa cevap harfi hiç yazılamıyor.
                put("max_completion_tokens", 1024)
                put("reasoning_effort", "low")
            }.toString()

        internal fun geminiGovdesi(model: String, soru: String, siklar: List<String>): String =
            JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SISTEM_ISTEMI))))
                put("contents", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", kullaniciIstemi(soru, siklar))))
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0)
                    put("maxOutputTokens", 256)
                    // Düşünmeyi en aza indir: hız için. "flash-lite" modelleri
                    // MINIMAL'i kabul ediyor.
                    put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal"))
                })
            }.toString()

        /** Başarılı yanıttan modelin yazdığı metin. */
        internal fun cevapMetni(sag: Saglayici, govde: String): String? {
            val j = JSONObject(govde)
            return when (sag) {
                Saglayici.GROQ -> j.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")
                Saglayici.GEMINI -> {
                    val parts = j.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
                    // Düşünce parçaları ("thought": true) cevap değil.
                    (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
                        .filterNot { it.optBoolean("thought") }
                        .joinToString("") { it.optString("text") }
                }
            }?.takeIf { it.isNotBlank() }
        }

        /**
         * Modelin yanıtını şık sırasına çevirir; anlaşılmazsa null.
         *
         * Model kurala uyup tek harf yazmalı, ama "B)", "Cevap: B", "**B**"
         * gibi süslemeler de kabul ediliyor. Harf yoksa şık metninin
         * kendisi aranıyor ("Ankara").
         */
        internal fun harfiCoz(yanit: String, siklar: List<String>): Int? {
            val n = siklar.size
            if (n == 0) return null
            val temiz = yanit.trim()
            // Tek harf: en sık durum.
            val sade = temiz.trim('*', '"', '\'', '`', '.', ')', '(', ' ', ':', '\n')
            if (sade.length == 1) {
                val i = sade[0].uppercaseChar() - 'A'
                return i.takeIf { it in 0 until n }
            }
            // "Cevap: B", "B) Ankara", "**B**" — tek başına duran ilk büyük harf.
            val harf = Regex("(?<![A-Za-zÇĞİÖŞÜçğıöşü])([A-Z])(?![A-Za-zÇĞİÖŞÜçğıöşü])")
                .findAll(temiz)
                .map { it.groupValues[1][0] - 'A' }
                .firstOrNull { it in 0 until n }
            if (harf != null) return harf
            // Harf yok: şık metni yazılmış olabilir.
            val k = TurkishText.normalizeKey(temiz)
            if (k.isEmpty()) return null
            val eslesen = siklar.indices.filter {
                val sk = TurkishText.normalizeKey(siklar[it])
                sk.isNotEmpty() && (sk == k || (sk.length >= 3 && k.contains(sk)))
            }
            return eslesen.singleOrNull()
        }

        /**
         * Yanıttaki güven sayısı (0-100): "B 85" → 85. Birden çok sayı varsa
         * sonuncusu (şık metni sayı olabilir: "C) 1923 90"). Yoksa null.
         */
        internal fun guvenCoz(yanit: String): Int? =
            Regex("(?<![0-9])([0-9]{1,3})(?![0-9])").findAll(yanit)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .lastOrNull { it in 0..100 }

        /**
         * Hata kodundan sonra bu sağlayıcı/model ne kadar süre denenmesin.
         *
         *  - 429 (kota / hız sınırı): günlük kota dolduysa bir saat, değilse
         *    sunucunun söylediği kadar (en az 20 sn).
         *  - 401/403 (anahtar geçersiz): 10 dakika — anahtarı düzeltmen için.
         *  - 404/400 (model kalkmış ya da istek reddedildi): 30 dakika.
         *  - 5xx, zaman aşımı: ceza yok, geçici.
         */
        internal fun cezaSuresiMs(kod: Int, govde: String): Long {
            val g = govde.lowercase()
            return when (kod) {
                429 -> when {
                    "per day" in g || "perday" in g || "daily" in g || "tokens per day" in g -> 60 * 60_000L
                    else -> {
                        val sn = Regex("retry-after=(\\d+)").find(g)?.groupValues?.get(1)?.toLongOrNull()
                            ?: Regex("try again in ([0-9.]+)s").find(g)?.groupValues?.get(1)
                                ?.toDoubleOrNull()?.toLong()
                            ?: Regex("\"retrydelay\"\\s*:\\s*\"(\\d+)").find(g)?.groupValues?.get(1)?.toLongOrNull()
                        ((sn ?: 60L).coerceIn(20L, 3600L)) * 1000L
                    }
                }
                401, 403 -> 10 * 60_000L
                400, 404 -> 30 * 60_000L
                else -> 0L
            }
        }

        private fun hataOzeti(kod: Int, govde: String): String {
            val mesaj = runCatching {
                val e = JSONObject(govde).opt("error")
                when (e) {
                    is JSONObject -> e.optString("message")
                    is String -> e
                    else -> null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: govde
            val kisa = mesaj.replace('\n', ' ').take(90)
            return when (kod) {
                -1 -> "bağlantı: $kisa"
                429 -> "kota doldu (429) · $kisa"
                401, 403 -> "anahtar reddedildi ($kod) · $kisa"
                else -> "HTTP $kod · $kisa"
            }
        }
    }
}
