package com.emre.bilbakalim.arsiv.data

import android.content.Context
import com.emre.bilbakalim.arsiv.capture.YapayZeka
import com.emre.bilbakalim.arsiv.util.Importers
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Arşivi yapay zekâyla toplu kontrol.
 *
 * Seçilen kategorideki (cevabı olan / olmayan / hepsi) sorular 25'erli
 * partiler hâlinde **hem Groq'a hem Gemini'ye** soruluyor:
 *
 *  - İkisi aynı şıkkı derse cevap kaydediliyor. Kayıtta zaten cevap varsa
 *    ve oyunda hiç gözlenmemişse (içe aktarım, eski yapay zekâ cevabı)
 *    düzeltiliyor; oyunda renkten görülen ya da elle seçilen cevapla
 *    çelişiyorsa senin kararına bırakılıyor.
 *  - Farklı şık derlerse (ya da biri cevap veremezse) soru listeye
 *    düşüyor, sen seçiyorsun.
 *
 * İş uygulama süreci boyunca sürüyor (ekrandan çıksan da); bekleyen liste
 * dosyada tutulduğu için uygulama kapansa da kaybolmuyor. Kota oyunla
 * ortak: bir anahtarın kotası dolunca sıradakine geçiliyor, hepsi doluysa
 * açılması bekleniyor.
 */
class TopluKontrol private constructor(context: Context) {

    /** Kararını bekleyen soru. Şık sıraları [siklar] listesine göre. */
    data class Bekleyen(
        val id: Long,
        val soru: String,
        val siklar: List<String>,
        val kayitli: Int?,
        val kaynak: String?,
        val groq: Int?,
        val gemini: Int?,
        val neden: String
    )

    data class Durum(
        val calisiyor: Boolean = false,
        val toplam: Int = 0,
        val islenen: Int = 0,
        /** Cevabı yoktu, iki yapay zekâ aynı dedi, yazıldı. */
        val kaydedilen: Int = 0,
        /** Oyunda gözlenmemiş cevap, iki yapay zekâ başka dedi, düzeltildi. */
        val duzeltilen: Int = 0,
        /** Kayıttaki cevap doğrulandı. */
        val ayni: Int = 0,
        /** Karar sende (listeye düştü). */
        val listeye: Int = 0,
        /** Anlık durum ya da bitiş mesajı. */
        val mesaj: String? = null
    )

    private val repo = Repo.get(context)
    private val prefs = Prefs.get(context)
    private val dosya = File(context.filesDir, "yapay_zeka_kontrol_bekleyen.json")
    private val yz = YapayZeka({})
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var is_: Job? = null

    private val _durum = MutableStateFlow(Durum())
    val durum: StateFlow<Durum> = _durum

    private val _bekleyenler = MutableStateFlow(bekleyenleriOku())
    val bekleyenler: StateFlow<List<Bekleyen>> = _bekleyenler

    /**
     * Kontrolü başlatır. [kategori] null ve [etiketsiz] false ise bütün
     * arşiv. [cevap]: 0 hepsi, 1 cevabı olmayanlar, 2 cevabı olanlar.
     */
    fun baslat(kategori: String?, etiketsiz: Boolean, cevap: Int) {
        if (is_?.isActive == true) return
        is_ = scope.launch {
            try {
                calis(kategori, etiketsiz, cevap)
            } catch (e: CancellationException) {
                _durum.value = _durum.value.copy(calisiyor = false, mesaj = "Durduruldu.")
                throw e
            } catch (t: Throwable) {
                _durum.value = _durum.value.copy(calisiyor = false, mesaj = "Hata: ${t.message}")
            }
        }
    }

    fun durdur() {
        is_?.cancel()
    }

    private suspend fun calis(kategori: String?, etiketsiz: Boolean, cevap: Int) {
        val s = prefs.state.value
        val groq = YapayZeka.anahtarSirasi(s.groqKeys, emptyList())
        val gemini = YapayZeka.anahtarSirasi(emptyList(), s.geminiKeys)
        if (groq.isEmpty() || gemini.isEmpty()) {
            _durum.value = Durum(
                mesaj = "İki yapay zekânın da cevabı karşılaştırıldığı için hem Groq " +
                    "hem Gemini anahtarı gerekli."
            )
            return
        }
        // Listede zaten bekleyen sorular yeniden sorulmuyor.
        val bekleyenIdler = _bekleyenler.value.map { it.id }.toSet()
        val adaylar = repo.kontrolAdaylari(kategori, etiketsiz, cevap)
            .filter { it.options.size >= 2 && it.id !in bekleyenIdler }
        _durum.value = Durum(calisiyor = true, toplam = adaylar.size,
            mesaj = if (adaylar.isEmpty()) null else "Soruluyor…")
        if (adaylar.isEmpty()) {
            _durum.value = Durum(mesaj = "Bu seçimde kontrol edilecek soru yok.")
            return
        }

        for (parti in adaylar.chunked(YapayZeka.TOPLU_PARTI)) {
            val sorular = parti.map { it.questionText to it.options }
            val (g, m) = coroutineScope {
                val a = async { partiyiSor(groq, sorular, "Groq") }
                val b = async { partiyiSor(gemini, sorular, "Gemini") }
                a.await() to b.await()
            }
            if (g == null || m == null) {
                // partiyiSor sebebini mesaja yazdı; işlenenler zaten kaydedildi.
                _durum.value = _durum.value.copy(calisiyor = false)
                return
            }
            parti.forEachIndexed { i, q -> karar(q, g[i], m[i]) }
            _durum.value = _durum.value.copy(islenen = _durum.value.islenen + parti.size, mesaj = "Soruluyor…")
        }
        val d = _durum.value
        _durum.value = d.copy(
            calisiyor = false,
            mesaj = "Bitti: ${d.kaydedilen} cevap kaydedildi, ${d.duzeltilen} düzeltildi, " +
                "${d.ayni} doğrulandı, ${d.listeye} soru senin kararını bekliyor."
        )
    }

    /**
     * Bir partiyi bir sağlayıcıya sorar (anahtarlar arasında dönerek, kota
     * bekleyerek). Yapılamazsa null; sebebi mesaja yazılır.
     */
    private suspend fun partiyiSor(
        anahtarlar: List<YapayZeka.Anahtar>,
        sorular: List<Pair<String, List<String>>>,
        ad: String
    ): List<Int?>? = yz.donerekSor(
        anahtarlar, ad,
        bildir = { _durum.value = _durum.value.copy(mesaj = it) },
        dene = { a, model -> yz.jsonSor(a, model, YapayZeka.TOPLU_SISTEM, YapayZeka.topluIstem(sorular)) },
        coz = { metin ->
            YapayZeka.topluCoz(metin, sorular.map { it.second.size })
                // Hiçbirini anlayamadıysa cevap bozuk: başka anahtar denensin.
                .takeIf { l -> l.any { it != null } }
        }
    )

    private suspend fun karar(q: QuestionEntity, g: Int?, m: Int?) {
        val d = _durum.value
        when (val k = kararVer(q.correctIndex, q.answerSource, q.edited, g, m)) {
            Karar.KAYDET -> {
                repo.yapayZekaCevabiYaz(q.id, g!!)
                _durum.value = d.copy(kaydedilen = d.kaydedilen + 1)
            }
            Karar.DUZELT -> {
                repo.yapayZekaCevabiYaz(q.id, g!!)
                _durum.value = d.copy(duzeltilen = d.duzeltilen + 1)
            }
            Karar.AYNI -> _durum.value = d.copy(ayni = d.ayni + 1)
            is Karar.Listele -> {
                ekle(Bekleyen(q.id, q.questionText, q.options, q.correctIndex, q.answerSource, g, m, k.neden))
                _durum.value = d.copy(listeye = d.listeye + 1)
            }
        }
    }

    /** Listeden karar: [index] null ise soru olduğu gibi kalır. */
    fun sec(b: Bekleyen, index: Int?) {
        scope.launch {
            if (index != null) {
                val q = repo.byId(b.id)
                // Kayıt bu arada değiştiyse (şıklar düzeltildi, silindi)
                // listedeki sıra artık geçerli değil.
                if (q != null && q.options == b.siklar) repo.elleCevapYaz(b.id, index)
            }
            synchronized(this@TopluKontrol) {
                _bekleyenler.value = _bekleyenler.value.filterNot { it.id == b.id }
                bekleyenleriYaz()
            }
        }
    }

    fun listeyiTemizle() {
        synchronized(this) {
            _bekleyenler.value = emptyList()
            bekleyenleriYaz()
        }
    }

    private fun ekle(b: Bekleyen) = synchronized(this) {
        _bekleyenler.value = _bekleyenler.value.filterNot { it.id == b.id } + b
        bekleyenleriYaz()
    }

    private fun bekleyenleriYaz() {
        runCatching { dosya.writeText(yaz(_bekleyenler.value)) }
    }

    private fun bekleyenleriOku(): List<Bekleyen> =
        runCatching { if (dosya.exists()) oku(dosya.readText()) else emptyList() }.getOrDefault(emptyList())

    sealed interface Karar {
        data object KAYDET : Karar
        data object DUZELT : Karar
        data object AYNI : Karar
        data class Listele(val neden: String) : Karar
    }

    companion object {
        /** Oyunda hiç gözlenmemiş kaynaklar: iki yapay zekâ bunları düzeltebilir. */
        private val ZAYIF = setOf(null, Importers.ANSWER_SOURCE, Repo.YAPAY_ZEKA)

        /**
         * Bir sorunun kaderi. [g] Groq'un, [m] Gemini'nin cevabı.
         */
        internal fun kararVer(kayitli: Int?, kaynak: String?, elle: Boolean, g: Int?, m: Int?): Karar {
            val zayif = !elle && kaynak in ZAYIF
            if (g != null && g == m) {
                return when {
                    kayitli == null -> Karar.KAYDET
                    kayitli == g -> Karar.AYNI
                    zayif -> Karar.DUZELT
                    else -> Karar.Listele(
                        if (elle) "ikisi de başka diyor; kayıttaki cevabı sen seçmiştin"
                        else "ikisi de başka diyor; kayıttaki cevap oyunda görülmüştü ($kaynak)"
                    )
                }
            }
            // Ayrıştılar. Oyunda görülmüş cevabı biri destekliyorsa ona
            // güveniyoruz; listeyi boş yere doldurmasın.
            if (kayitli != null && !zayif && (kayitli == g || kayitli == m)) return Karar.AYNI
            return Karar.Listele(
                when {
                    g == null && m == null -> "ikisi de cevap veremedi"
                    g == null -> "Groq cevap veremedi"
                    m == null -> "Gemini cevap veremedi"
                    else -> "farklı cevap verdiler"
                }
            )
        }

        internal fun yaz(l: List<Bekleyen>): String = JSONArray().apply {
            l.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("soru", b.soru)
                    put("siklar", JSONArray(b.siklar))
                    put("kayitli", b.kayitli ?: JSONObject.NULL)
                    put("kaynak", b.kaynak ?: JSONObject.NULL)
                    put("groq", b.groq ?: JSONObject.NULL)
                    put("gemini", b.gemini ?: JSONObject.NULL)
                    put("neden", b.neden)
                })
            }
        }.toString()

        internal fun oku(metin: String): List<Bekleyen> {
            val a = JSONArray(metin)
            return (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val s = o.getJSONArray("siklar")
                fun intOrNull(k: String) = if (o.isNull(k)) null else o.getInt(k)
                Bekleyen(
                    id = o.getLong("id"),
                    soru = o.getString("soru"),
                    siklar = (0 until s.length()).map { s.getString(it) },
                    kayitli = intOrNull("kayitli"),
                    kaynak = if (o.isNull("kaynak")) null else o.getString("kaynak"),
                    groq = intOrNull("groq"),
                    gemini = intOrNull("gemini"),
                    neden = o.optString("neden")
                )
            }
        }

        @Volatile private var INSTANCE: TopluKontrol? = null
        fun get(context: Context): TopluKontrol =
            INSTANCE ?: synchronized(this) { INSTANCE ?: TopluKontrol(context.applicationContext).also { INSTANCE = it } }
    }
}
