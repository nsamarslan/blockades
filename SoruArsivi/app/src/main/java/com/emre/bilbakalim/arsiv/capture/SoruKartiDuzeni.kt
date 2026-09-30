package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.util.TurkishText
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dışa aktarılan soru görselinin kararları: hangi hap doğru şık, görüntünün
 * neresi kırpılacak. Çizim `util/SoruGorseli`'nde; buradaki her şey piksel
 * dizileri ve sayılarla çalışıyor, Android'e bağlı değil, birim testte
 * denenebiliyor.
 */
object SoruKartiDuzeni {

    /** Hapın zemin rengi. Oyun kararı açınca doğru şık yeşile, yanlış seçilen kırmızıya dönüyor. */
    enum class HapRengi { BEYAZ, SECILI, YESIL, KIRMIZI, BILINMIYOR }

    /**
     * Ortalama zemin renginden hapın hâli.
     *
     * Ölçülen renkler (tarama günlüğü): beyaz (248,248,248); basılı
     * (248,216,88); karar yeşili (136,248,136) ve (88,248,72); doğru yeşili
     * (120,240,170); kırmızı (248,104,104). Turkuaz (110,240,220) yeşilden
     * tonuyla ayrılıyor (172° / 145°).
     */
    fun hapRengi(r: Int, g: Int, b: Int): HapRengi {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max < 150) return HapRengi.BILINMIYOR
        val s = (max - min) / max.toFloat()
        if (s < 0.18f) return HapRengi.BEYAZ
        if (s < 0.25f) return HapRengi.BILINMIYOR
        val d = (max - min).toFloat()
        val ton = when (max) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return when {
            ton <= 20f || ton >= 340f -> HapRengi.KIRMIZI
            ton in 80f..160f -> HapRengi.YESIL
            ton in 30f..75f -> HapRengi.SECILI
            else -> HapRengi.BILINMIYOR
        }
    }

    /**
     * Hapın içinden alınmış piksellerin (ARGB) zemin rengi: yazı (koyu)
     * pikselleri atlanıyor. Zemin pikseli yoksa null.
     */
    fun zeminRengi(px: IntArray): HapRengi? {
        var n = 0
        var rs = 0L
        var gs = 0L
        var bs = 0L
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (!OptionBoxFinder.isPill(r, g, b)) continue
            n++; rs += r; gs += g; bs += b
        }
        if (n < 20) return null
        return hapRengi((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
    }

    /** Doğru şık kararı ve nereden geldiği. */
    data class Karar(val sira: Int, val kaynak: Kaynak)

    enum class Kaynak {
        /** Görüntüde oyun doğru şıkkı yeşile boyamış. */
        RENK,
        /** Arşivdeki doğru cevabın metni görüntüdeki şıklardan birine eşleşti. */
        METIN
    }

    /**
     * Görüntüdeki hangi hap doğru şık?
     *
     * Görüntüde tek bir yeşil hap varsa o: oyunun kendi kararı, arşivden
     * bile sağlam. (Seçilip yanlış çıkan şık kırmızı, doğrusu yeşil: ok
     * yeşile gidiyor.) Yoksa arşivdeki doğru cevabın metni, görüntüdeki
     * şık metinlerinde aranıyor ([SikEsleme.ekrandaBul], bilinen cevaba
     * basan botla aynı kural). Emin olunamıyorsa null: yanlış yeri
     * göstermektense hiç göstermemek.
     */
    fun dogruKutu(
        renkler: List<HapRengi?>,
        kayitSiklari: List<String>,
        kayitImzalari: List<String?>,
        dogru: Int?,
        ekranSiklari: List<String>?,
        ekranImzalari: List<String?>
    ): Karar? {
        val yesiller = renkler.indices.filter { renkler[it] == HapRengi.YESIL }
        if (yesiller.size == 1) return Karar(yesiller[0], Kaynak.RENK)
        if (dogru == null || ekranSiklari == null || ekranSiklari.size != renkler.size) return null
        val sira = SikEsleme.ekrandaBul(kayitSiklari, kayitImzalari, dogru, ekranSiklari, ekranImzalari)
            ?: return null
        return Karar(sira, Kaynak.METIN)
    }

    /** Soru kartının üst kenarı ve yatay sınırları (görüntü pikseli). */
    data class Kart(val ust: Int, val sol: Int, val sag: Int)

    /**
     * Şıkların üstündeki beyaz soru kartını satır ölçümlerinden bulur:
     * ilk haptan yukarı çıkarken önce aradaki mor boşluk geçiliyor, sonra
     * geniş beyaz satırlar kartın kendisi. Kartın içindeki yazı satırları
     * (koyu) kısa kopukluklar olarak hoş görülüyor. Bulunamazsa null.
     */
    fun kartBul(satirlar: List<OptionBoxFinder.Row>, ilkKutuUst: Int, w: Int, h: Int): Kart? {
        val yukari = satirlar.filter { it.y < ilkKutuUst }.sortedByDescending { it.y }
        var i = 0
        while (i < yukari.size && !OptionBoxFinder.isBoxRow(yukari[i], w) &&
            ilkKutuUst - yukari[i].y < BOSLUK_MAX * h
        ) i++
        if (i >= yukari.size || !OptionBoxFinder.isBoxRow(yukari[i], w)) return null
        var ust = yukari[i].y
        var sol = yukari[i].left
        var sag = yukari[i].right
        var kopukBas = -1
        while (i < yukari.size) {
            val r = yukari[i]
            if (OptionBoxFinder.isBoxRow(r, w)) {
                ust = r.y
                sol = minOf(sol, r.left)
                sag = maxOf(sag, r.right)
                kopukBas = -1
            } else {
                if (kopukBas < 0) kopukBas = r.y
                if (kopukBas - r.y > KOPUKLUK_MAX * h) break
            }
            i++
        }
        // Tek bir iki satırlık "kart" gürültüdür.
        if (ilkKutuUst - ust < KART_MIN * h) return null
        return Kart(ust, sol, sag)
    }

    /** Kırpılacak dikdörtgen: [sol, ust, sag, alt). */
    data class Kirpma(val sol: Int, val ust: Int, val sag: Int, val alt: Int) {
        val w: Int get() = sag - sol
        val h: Int get() = alt - ust
    }

    /**
     * Soru kartı (soru numarasıyla birlikte) ve şıklar. Kart bulunamadıysa
     * ilk hapın epey üstünden, bütün genişlik: soru yarım kalmasın.
     */
    fun kirpma(kutular: List<OptionBoxFinder.Box>, kart: Kart?, w: Int, h: Int): Kirpma {
        val ilk = kutular.minOf { it.top }
        val son = kutular.maxOf { it.bottom }
        val ust = if (kart != null) kart.ust - (UST_PAY * h).toInt() else ilk - (KARTSIZ_UST * h).toInt()
        val alt = son + (ALT_PAY * h).toInt()
        val (sol, sag) = if (kart != null) {
            val pay = (YAN_PAY * w).toInt()
            minOf(kart.sol, kutular.minOf { it.left }) - pay to maxOf(kart.sag, kutular.maxOf { it.right }) + pay
        } else 0 to w
        return Kirpma(sol.coerceIn(0, w - 1), ust.coerceIn(0, h - 1), sag.coerceIn(1, w), alt.coerceIn(1, h))
            .takeIf { it.w > 10 && it.h > 10 } ?: Kirpma(0, 0, w, h)
    }

    /** Kart ile ilk hap arasındaki mor boşluk en fazla ekran boyunun bu kadarı. */
    private const val BOSLUK_MAX = 0.12f
    /** Kartın içinde yazı satırı sayılan en uzun kopukluk. */
    private const val KOPUKLUK_MAX = 0.03f
    /** Bundan kısa "kart" gürültü. */
    private const val KART_MIN = 0.06f
    /** Kartın üstünde soru numarası (kartın hemen üstündeki rozet) için pay. */
    private const val UST_PAY = 0.06f
    private const val ALT_PAY = 0.015f
    private const val YAN_PAY = 0.02f
    /** Kart bulunamadığında ilk hapın ne kadar üstünden başlanacağı. */
    private const val KARTSIZ_UST = 0.45f

    /**
     * Ekran görüntüsünün yanında duran şık yerleşimi: şıkların ekrandaki
     * sırası ve kutuları, **kaydedilen görüntünün** pikselleriyle.
     *
     * Yakalama anında yazılıyor; o an şıkların metni zaten biliniyor. Eski
     * görüntülerde dışa aktarım OCR'la bulup bunu yazıyor, bir sonraki dışa
     * aktarım OCR'sız geçiyor.
     */
    data class SikYerlesimi(
        val siklar: List<String>,
        val kutular: List<OptionBoxFinder.Box>,
        /** "kutu": haplar pikselden ölçüldü; "metin": kutular yazının sınırı. */
        val yol: String
    ) {
        fun json(): String = JSONObject().apply {
            put("surum", 1)
            put("yol", yol)
            put("siklar", JSONArray(siklar))
            put("kutular", JSONArray().apply {
                for (k in kutular) put(JSONArray(listOf(k.left, k.top, k.right, k.bottom)))
            })
        }.toString()

        /** Görüntüye sığıyor ve şık sayısı kutu sayısını tutuyor mu? */
        fun gecerli(w: Int, h: Int): Boolean =
            siklar.size == kutular.size && siklar.size >= 2 &&
                kutular.all { it.left >= 0 && it.top >= 0 && it.right <= w && it.bottom <= h && it.width > 0 && it.height > 0 }

        companion object {
            fun oku(json: String): SikYerlesimi? = runCatching {
                val o = JSONObject(json)
                val s = o.getJSONArray("siklar")
                val k = o.getJSONArray("kutular")
                SikYerlesimi(
                    (0 until s.length()).map { s.getString(it) },
                    (0 until k.length()).map {
                        val a = k.getJSONArray(it)
                        OptionBoxFinder.Box(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
                    },
                    o.optString("yol", "kutu")
                )
            }.getOrNull()
        }
    }

    /** OCR'dan gelen kutu metni, ayrıştırıcının şık temizliğiyle; boşsa «(okunamadı)». */
    fun sikMetni(parcalar: List<Pair<String, IntRange>>): String =
        TurkishText.stripOptionPrefix(TurkishText.cleanOcr(QuestionParser.kutuMetni(parcalar)))
            .ifBlank { TurkishText.UNREADABLE_OPTION }
}
