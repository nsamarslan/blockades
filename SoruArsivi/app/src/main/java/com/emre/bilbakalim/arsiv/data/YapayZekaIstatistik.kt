package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Yapay zekânın gerçek başarısı: oyunun gösterdiği doğru cevapla
 * karşılaştırılan tahminler.
 *
 * Yalnızca kesin kanıtla (karar yeşili, kırmızı, süre dolunca ayrışan şık)
 * öğrenilen cevaplar sayılıyor; "dokunduğun şık öyle kaldı" gibi zayıf
 * kanıt yapay zekâyı haksız yere doğru ya da yanlış gösterirdi.
 *
 * Sağlayıcı (Groq / Gemini), kategori ve modelin söylediği güven aralığı
 * ayrı ayrı tutuluyor: hangi sağlayıcıya, hangi konuda ve hangi güvenin
 * üstünde güvenilebileceği veriyle görülsün, eşik ona göre ayarlansın.
 */
class YapayZekaIstatistik private constructor(context: Context) {

    /** Doğru / toplam. */
    data class Sayac(val dogru: Int = 0, val toplam: Int = 0) {
        val yuzde: Int? get() = if (toplam == 0) null else dogru * 100 / toplam
        fun ekle(dogruMu: Boolean) = Sayac(dogru + if (dogruMu) 1 else 0, toplam + 1)
    }

    /** Bir sağlayıcının sayaçları. */
    data class Saglayici(
        val toplam: Sayac = Sayac(),
        val kategori: Map<String, Sayac> = emptyMap(),
        /** Anahtar [guvenAraligi] etiketi. */
        val guven: Map<String, Sayac> = emptyMap()
    )

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("yapay_zeka_istatistik", Context.MODE_PRIVATE)

    private val _durum = MutableStateFlow(oku(sp.getString(K_VERI, null)))
    /** Sağlayıcı adı ("Groq", "Gemini") → sayaçlar. */
    val durum: StateFlow<Map<String, Saglayici>> = _durum

    /** Bir tahmini sonucuyla kaydeder. */
    @Synchronized
    fun ekle(saglayici: String, kategori: String?, guven: Int?, dogruMu: Boolean) {
        val yeni = eklenmis(_durum.value, saglayici, kategori, guven, dogruMu)
        sp.edit().putString(K_VERI, yaz(yeni)).apply()
        _durum.value = yeni
    }

    @Synchronized
    fun sifirla() {
        sp.edit().remove(K_VERI).apply()
        _durum.value = emptyMap()
    }

    companion object {
        private const val K_VERI = "veri"
        const val ETIKETSIZ = "Etiketsiz"

        /** Güven aralıkları, ekranda bu sırayla. */
        val GUVEN_ARALIKLARI = listOf("%90-100", "%70-89", "%50-69", "%0-49", "güven yok")

        internal fun guvenAraligi(guven: Int?): String = when {
            guven == null -> "güven yok"
            guven >= 90 -> "%90-100"
            guven >= 70 -> "%70-89"
            guven >= 50 -> "%50-69"
            else -> "%0-49"
        }

        internal fun eklenmis(
            eski: Map<String, Saglayici>,
            saglayici: String,
            kategori: String?,
            guven: Int?,
            dogruMu: Boolean
        ): Map<String, Saglayici> {
            val s = eski[saglayici] ?: Saglayici()
            val k = kategori?.takeIf { it.isNotBlank() } ?: ETIKETSIZ
            val g = guvenAraligi(guven)
            val yeni = Saglayici(
                toplam = s.toplam.ekle(dogruMu),
                kategori = s.kategori + (k to (s.kategori[k] ?: Sayac()).ekle(dogruMu)),
                guven = s.guven + (g to (s.guven[g] ?: Sayac()).ekle(dogruMu))
            )
            return eski + (saglayici to yeni)
        }

        internal fun yaz(veri: Map<String, Saglayici>): String = JSONObject().apply {
            for ((ad, s) in veri) {
                put(ad, JSONObject().apply {
                    put("toplam", sayacJson(s.toplam))
                    put("kategori", JSONObject().apply { s.kategori.forEach { (k, v) -> put(k, sayacJson(v)) } })
                    put("guven", JSONObject().apply { s.guven.forEach { (k, v) -> put(k, sayacJson(v)) } })
                })
            }
        }.toString()

        internal fun oku(metin: String?): Map<String, Saglayici> {
            if (metin.isNullOrBlank()) return emptyMap()
            return runCatching {
                val j = JSONObject(metin)
                j.keys().asSequence().associateWith { ad ->
                    val s = j.getJSONObject(ad)
                    Saglayici(
                        toplam = sayac(s.optJSONObject("toplam")),
                        kategori = harita(s.optJSONObject("kategori")),
                        guven = harita(s.optJSONObject("guven"))
                    )
                }
            }.getOrDefault(emptyMap())
        }

        private fun sayacJson(s: Sayac) = JSONObject().put("d", s.dogru).put("t", s.toplam)
        private fun sayac(j: JSONObject?) = if (j == null) Sayac() else Sayac(j.optInt("d"), j.optInt("t"))
        private fun harita(j: JSONObject?): Map<String, Sayac> =
            j?.keys()?.asSequence()?.associateWith { sayac(j.optJSONObject(it)) } ?: emptyMap()

        @Volatile private var INSTANCE: YapayZekaIstatistik? = null
        fun get(context: Context): YapayZekaIstatistik =
            INSTANCE ?: synchronized(this) { INSTANCE ?: YapayZekaIstatistik(context).also { INSTANCE = it } }
    }
}
