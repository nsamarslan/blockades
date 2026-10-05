package com.emre.bilbakalim.arsiv.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Her yapay zekâ anahtarının bugünkü kullanımı: kaç istek gitti, kaçı
 * kotaya takıldı, Groq'un bildirdiği kalan günlük istek, son hata.
 * Ayarlardaki "anahtarların durumu" listesi bunu gösteriyor; "neden yapay
 * zekâ cevap vermedi" sorusu günlüğe bakmadan cevaplansın diye.
 *
 * Anahtarın kendisi değil özeti saklanıyor. Sayaçlar gün değişince
 * sıfırlanıyor (telefonun saatine göre; Groq'un günlük kotası da kayan
 * bir pencere, tam gece yarısı açılmıyor).
 */
object AnahtarKotasi {

    data class Durum(
        val gun: String = "",
        val istek: Int = 0,
        val basarili: Int = 0,
        val kotaHatasi: Int = 0,
        /** Groq başlığı: kalan günlük istek ve günlük sınır. */
        val kalanIstek: Int? = null,
        val istekSiniri: Int? = null,
        val sonHata: String? = null,
        val sonHataAt: Long = 0L,
        val sonBasariAt: Long = 0L
    )

    private var sp: SharedPreferences? = null
    private val _durumlar = MutableStateFlow<Map<String, Durum>>(emptyMap())
    /** Anahtar özeti ([ozet]) → bugünkü durum. */
    val durumlar: StateFlow<Map<String, Durum>> = _durumlar

    /** Uygulama açılırken bir kez. Çağrılmazsa (birim test) sayaç tutulmaz. */
    fun baslat(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences("anahtar_kotasi", Context.MODE_PRIVATE)
        sp = p
        _durumlar.value = p.all.mapNotNull { (k, v) ->
            (v as? String)?.let { oku(it) }?.let { k to it }
        }.toMap()
    }

    /** Anahtarı saklamadan tanımak için: sağlayıcı + değerin özeti. */
    fun ozet(saglayici: String, anahtar: String): String = "${saglayici}_${anahtar.trim().hashCode()}"

    fun bugun(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    /**
     * Bir isteğin sonucunu yazar. [kod] HTTP kodu (-1: bağlantı hatası).
     */
    @Synchronized
    fun kaydet(
        saglayici: String,
        anahtar: String,
        kod: Int,
        hata: String?,
        kalanIstek: Int?,
        istekSiniri: Int?
    ) {
        val p = sp ?: return
        val k = ozet(saglayici, anahtar)
        val yeni = guncelle(_durumlar.value[k], bugun(), System.currentTimeMillis(), kod, hata, kalanIstek, istekSiniri)
        p.edit().putString(k, yaz(yeni)).apply()
        _durumlar.value = _durumlar.value + (k to yeni)
    }

    internal fun guncelle(
        eski: Durum?,
        gun: String,
        simdi: Long,
        kod: Int,
        hata: String?,
        kalanIstek: Int?,
        istekSiniri: Int?
    ): Durum {
        val d = eski?.takeIf { it.gun == gun } ?: Durum(gun = gun)
        return d.copy(
            istek = d.istek + 1,
            basarili = d.basarili + if (kod == 200) 1 else 0,
            kotaHatasi = d.kotaHatasi + if (kod == 429) 1 else 0,
            kalanIstek = kalanIstek ?: d.kalanIstek,
            istekSiniri = istekSiniri ?: d.istekSiniri,
            sonHata = if (kod == 200) d.sonHata else hata,
            sonHataAt = if (kod == 200) d.sonHataAt else simdi,
            sonBasariAt = if (kod == 200) simdi else d.sonBasariAt
        )
    }

    /** Bugüne ait değilse boş durum: dünkü sayılar bugün gösterilmesin. */
    fun bugunku(d: Durum?): Durum = d?.takeIf { it.gun == bugun() } ?: Durum(gun = bugun())

    internal fun yaz(d: Durum): String = JSONObject().apply {
        put("gun", d.gun); put("istek", d.istek); put("basarili", d.basarili)
        put("kota", d.kotaHatasi)
        d.kalanIstek?.let { put("kalan", it) }
        d.istekSiniri?.let { put("sinir", it) }
        d.sonHata?.let { put("hata", it) }
        put("hataAt", d.sonHataAt); put("basariAt", d.sonBasariAt)
    }.toString()

    internal fun oku(s: String): Durum? = runCatching {
        val j = JSONObject(s)
        Durum(
            gun = j.optString("gun"),
            istek = j.optInt("istek"),
            basarili = j.optInt("basarili"),
            kotaHatasi = j.optInt("kota"),
            kalanIstek = if (j.has("kalan")) j.getInt("kalan") else null,
            istekSiniri = if (j.has("sinir")) j.getInt("sinir") else null,
            sonHata = if (j.has("hata")) j.getString("hata") else null,
            sonHataAt = j.optLong("hataAt"),
            sonBasariAt = j.optLong("basariAt")
        )
    }.getOrNull()
}
