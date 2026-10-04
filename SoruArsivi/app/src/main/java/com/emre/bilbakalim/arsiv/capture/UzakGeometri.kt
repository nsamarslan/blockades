package com.emre.bilbakalim.arsiv.capture

import com.emre.bilbakalim.arsiv.data.EkranBolgesi

/**
 * Uzak modun konum hesapları: yayının hangi parçası okunacak, kaç kat
 * büyütülecek, ok ve düğme nereye konacak.
 *
 * Android'e bağlı değil (yalnızca sayılar); birim testte denenebiliyor.
 */
object UzakGeometri {

    /** Piksel dikdörtgeni: [sol, ust, sag, alt). */
    data class Kutu(val sol: Int, val ust: Int, val sag: Int, val alt: Int) {
        val w: Int get() = sag - sol
        val h: Int get() = alt - ust
        val ortaY: Int get() = (ust + alt) / 2
        val ortaX: Int get() = (sol + sag) / 2
        fun kesisir(o: Kutu): Boolean = sol < o.sag && o.sol < sag && ust < o.alt && o.ust < alt
    }

    fun piksel(b: EkranBolgesi, w: Int, h: Int): Kutu =
        Kutu((b.sol * w).toInt(), (b.ust * h).toInt(), (b.sag * w).toInt(), (b.alt * h).toInt())

    /**
     * Okunacak parça: iki bölgenin birleşimi, her yana pay eklenmiş.
     *
     * Kutu ölçümü ve ayrıştırıcı ekranın tamamı için yazıldı; eşikleri
     * görüntü boyuna oranla (şık hapı boyun %3,5-13'ü, en üstteki %4,5
     * durum çubuğu sayılıp atılıyor, kenara dayanan hap kırpılmış sayılıyor).
     * Yayında oyun ekranın küçük bir köşesi; tam ekranda bakılsa haplar bu
     * eşiklerin çok altında kalırdı. Yalnızca oyunun kendisini kırpıp onu
     * bir ekranmış gibi okuyoruz. Pay, kenar kurallarının seçilen bölgeyi
     * kesmemesi için.
     */
    fun cerceve(soru: EkranBolgesi, sik: EkranBolgesi, w: Int, h: Int): Kutu {
        val s = piksel(soru, w, h)
        val k = piksel(sik, w, h)
        val sol = minOf(s.sol, k.sol)
        val ust = minOf(s.ust, k.ust)
        val sag = maxOf(s.sag, k.sag)
        val alt = maxOf(s.alt, k.alt)
        val pay = maxOf(((alt - ust) * PAY).toInt(), PAY_EN_AZ)
        return Kutu(
            (sol - pay).coerceAtLeast(0), (ust - pay).coerceAtLeast(0),
            (sag + pay).coerceAtMost(w), (alt + pay).coerceAtMost(h)
        )
    }

    /** [bolge]'nin [cerceve]'ye göre oranı (kırpılmış görüntüde nerede). */
    fun goreli(bolge: EkranBolgesi, cerceve: Kutu, w: Int, h: Int): EkranBolgesi {
        val b = piksel(bolge, w, h)
        val cw = cerceve.w.coerceAtLeast(1).toFloat()
        val ch = cerceve.h.coerceAtLeast(1).toFloat()
        return EkranBolgesi(
            ((b.sol - cerceve.sol) / cw).coerceIn(0f, 1f),
            ((b.ust - cerceve.ust) / ch).coerceIn(0f, 1f),
            ((b.sag - cerceve.sol) / cw).coerceIn(0f, 1f),
            ((b.alt - cerceve.ust) / ch).coerceIn(0f, 1f)
        )
    }

    /**
     * Kırpılan parça kaç kat büyütülsün? Yayında oyun küçük; ML Kit
     * küçük yazıyı düşürüyor. Telefondaki oyunda soru kartı ~900 piksel
     * genişliğinde okunuyordu; ona yaklaştırıyoruz, ama üç kattan fazla
     * büyütmek bulanık yayına bir şey katmıyor, yalnızca yavaşlatıyor.
     */
    fun olcek(cerceveW: Int): Int {
        if (cerceveW <= 0) return 1
        val k = (HEDEF_GENISLIK + cerceveW - 1) / cerceveW
        return k.coerceIn(1, OLCEK_EN_FAZLA)
    }

    /** Okun yeri ve hangi yöne baktığı. */
    data class Isaret(val x: Int, val y: Int, val w: Int, val h: Int, val sagaBakar: Boolean)

    /**
     * Doğru şıkkı gösteren okun yeri: şıkkın hizasında, okunan çerçevenin
     * **dışında**. Ok ekran görüntüsüne de giriyor; çerçevenin içine
     * çizilse yeşil ok bir sonraki karede hap pikseli sayılır ve şık
     * ölçümünü bozardı. Önce solda (sağa bakan ok), yer yoksa sağda.
     * İkisinde de yer yoksa null: o zaman yalnızca düğmedeki harf.
     */
    fun isaretYeri(hedef: Kutu, cerceve: Kutu, ekranW: Int, okW: Int, okH: Int): Isaret? {
        val y = (hedef.ortaY - okH / 2).coerceAtLeast(0)
        if (cerceve.sol >= okW) return Isaret(cerceve.sol - okW, y, okW, okH, sagaBakar = true)
        if (ekranW - cerceve.sag >= okW) return Isaret(cerceve.sag, y, okW, okH, sagaBakar = false)
        return null
    }

    /**
     * Yüzen düğme okunan çerçevenin üstüne bırakıldıysa dışarı iter: düğme
     * de ekran görüntüsüne giriyor. En kısa yolu seçer (sol, sağ, üst,
     * alt); hiçbir yanda yer yoksa düğme olduğu yerde kalır.
     */
    fun disariIt(dugme: Kutu, cerceve: Kutu, ekranW: Int, ekranH: Int): Pair<Int, Int> {
        if (!dugme.kesisir(cerceve)) return dugme.sol to dugme.ust
        val adaylar = listOfNotNull(
            (cerceve.sol - dugme.w).takeIf { it >= 0 }?.let { it to dugme.ust },
            cerceve.sag.takeIf { it + dugme.w <= ekranW }?.let { it to dugme.ust },
            (cerceve.ust - dugme.h).takeIf { it >= 0 }?.let { dugme.sol to it },
            cerceve.alt.takeIf { it + dugme.h <= ekranH }?.let { dugme.sol to it }
        )
        return adaylar.minByOrNull { (x, y) ->
            kotlin.math.abs(x - dugme.sol) + kotlin.math.abs(y - dugme.ust)
        } ?: (dugme.sol to dugme.ust)
    }

    /** Çerçeveye eklenen pay: birleşik yüksekliğin oranı. */
    private const val PAY = 0.06f
    private const val PAY_EN_AZ = 8
    private const val HEDEF_GENISLIK = 720
    private const val OLCEK_EN_FAZLA = 3
}
