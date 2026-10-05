package com.emre.bilbakalim.arsiv.capture

import android.os.SystemClock
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
 * Sırayla deneniyor: biri hata verirse, kotası dolarsa ("token hakkı
 * kalmadı", HTTP 429) ya da süre aşılırsa diğerine soruluyor. Kotası dolan
 * sağlayıcı bir süre hiç denenmiyor; her soruda boşuna bekleyip oyunun
 * süresini yememek için.
 *
 * İstem modelden yalnızca tek bir harf istiyor (A, B, C, D); dönen metin
 * [harfiCoz] ile şık sırasına çevriliyor. Model ne derse desin bot yine
 * ekrandaki şıklardan birine basıyor — cevap çözülemezse null dönüyor ve
 * çağıran taraf rastgeleye düşüyor.
 */
class YapayZeka(private val log: (String) -> Unit) {

    enum class Saglayici(val ad: String) { GROQ("Groq"), GEMINI("Gemini") }

    /** Bir sorunun sonucu. [index] null ise hiçbir sağlayıcı cevap veremedi. */
    data class Sonuc(
        val index: Int?,
        val saglayici: Saglayici?,
        val model: String?,
        val sureMs: Long,
        /** Denenip başarısız olanlar, günlük için ("Groq: kota doldu"). */
        val hatalar: List<String>
    )

    /** Sağlayıcı/model bazında "şu ana kadar deneme" (SystemClock.elapsedRealtime). */
    private val bekle = HashMap<String, Long>()

    suspend fun sor(
        soru: String,
        siklar: List<String>,
        groqAnahtari: String,
        geminiAnahtari: String,
        onceGemini: Boolean
    ): Sonuc = withContext(Dispatchers.IO) {
        val bas = SystemClock.elapsedRealtime()
        val hatalar = ArrayList<String>()
        val sira = buildList {
            if (groqAnahtari.isNotBlank()) add(Saglayici.GROQ)
            if (geminiAnahtari.isNotBlank()) add(Saglayici.GEMINI)
        }.let { if (onceGemini) it.reversed() else it }

        for (sag in sira) {
            val anahtar = (if (sag == Saglayici.GROQ) groqAnahtari else geminiAnahtari).trim()
            for (model in modeller(sag)) {
                val gecen = SystemClock.elapsedRealtime() - bas
                if (gecen > TOPLAM_BUTCE_MS) {
                    hatalar += "süre doldu"
                    return@withContext Sonuc(null, null, null, gecen, hatalar)
                }
                val k = "${sag.name}/$model"
                val simdi = SystemClock.elapsedRealtime()
                val kadar = synchronized(bekle) { bekle[k] ?: 0L }
                if (simdi < kadar) {
                    hatalar += "${sag.ad} $model: kota/anahtar sorunu, ${(kadar - simdi) / 1000} sn atlanıyor"
                    continue
                }
                // Süre sınırını bağlantının kendi zaman aşımları koyuyor.
                val yanit = runCatching { istek(sag, model, anahtar, soru, siklar) }
                    .getOrElse { Yanit(-1, it.message ?: it.javaClass.simpleName) }

                if (yanit.kod == 200) {
                    val metin = runCatching { cevapMetni(sag, yanit.govde) }.getOrNull()
                    val index = metin?.let { harfiCoz(it, siklar) }
                    if (index != null) {
                        return@withContext Sonuc(
                            index, sag, model, SystemClock.elapsedRealtime() - bas, hatalar
                        )
                    }
                    hatalar += "${sag.ad} $model: anlaşılmayan cevap «${metin?.take(40) ?: yanit.govde.take(80)}»"
                    continue
                }

                val ceza = cezaSuresiMs(yanit.kod, yanit.govde)
                if (ceza > 0) synchronized(bekle) { bekle[k] = SystemClock.elapsedRealtime() + ceza }
                hatalar += "${sag.ad} $model: ${hataOzeti(yanit.kod, yanit.govde)}"
                // Anahtar geçersizse aynı sağlayıcının öteki modelini
                // denemek boşuna.
                if (yanit.kod == 401 || yanit.kod == 403) {
                    modeller(sag).forEach { m ->
                        synchronized(bekle) { bekle["${sag.name}/$m"] = SystemClock.elapsedRealtime() + ceza }
                    }
                    break
                }
            }
        }
        Sonuc(null, null, null, SystemClock.elapsedRealtime() - bas, hatalar)
    }

    /** Anahtar değişince eski cezalar unutulsun. */
    fun sifirla() = synchronized(bekle) { bekle.clear() }

    private class Yanit(val kod: Int, val govde: String)

    private fun istek(
        sag: Saglayici, model: String, anahtar: String, soru: String, siklar: List<String>
    ): Yanit {
        val (url, govde) = when (sag) {
            Saglayici.GROQ -> GROQ_URL to groqGovdesi(model, soru, siklar)
            Saglayici.GEMINI ->
                "$GEMINI_URL${URLEncoder.encode(model, "UTF-8")}:generateContent" to
                    geminiGovdesi(model, soru, siklar)
        }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = BAGLANTI_SURESI_MS
            c.readTimeout = ISTEK_SURESI_MS
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
            val bekleSn = c.getHeaderField("retry-after")?.trim()?.toLongOrNull()
            return Yanit(kod, if (bekleSn != null && kod == 429) "$metin\nretry-after=$bekleSn" else metin)
        } catch (e: IOException) {
            return Yanit(-1, e.message ?: e.javaClass.simpleName)
        } finally {
            c.disconnect()
        }
    }

    companion object {
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
                "Yanıtın YALNIZCA doğru şıkkın tek büyük harfi olsun " +
                "(örneğin: B). Açıklama, nokta, şık metni ya da başka hiçbir şey yazma."

        internal fun kullaniciIstemi(soru: String, siklar: List<String>): String {
            val h = harfler(siklar.size)
            return buildString {
                append("Soru: ").append(soru.trim().replace('\n', ' ')).append("\n\n")
                siklar.forEachIndexed { i, s ->
                    append(h[i]).append(") ").append(s.trim().replace('\n', ' ')).append('\n')
                }
                append("\nYalnızca şu harflerden birini yaz: ").append(h.joinToString(", "))
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
