package com.emre.bilbakalim.arsiv.data

/**
 * Ekranın bir dikdörtgeni, ekran genişliği ve yüksekliğine oranla (0..1).
 *
 * "Ekranı ayarla" ekranında elle seçilen soru ve şık bölgeleri böyle
 * saklanıyor. Oran olduğu için ekran görüntüsünün ölçeğinden bağımsız:
 * küçültülmüş bir kare üstünde seçilen bölge tam ekrana aynen uyuyor.
 *
 * Android'e bağlı değil; birim testte denenebiliyor.
 */
data class EkranBolgesi(val sol: Float, val ust: Float, val sag: Float, val alt: Float) {

    val genislik: Float get() = sag - sol
    val yukseklik: Float get() = alt - ust

    /** Kullanılabilir bir bölge mi (ekranın içinde, çizgi değil)? */
    val gecerli: Boolean
        get() = sol >= 0f && ust >= 0f && sag <= 1f && alt <= 1f &&
            genislik >= EN_AZ_GENISLIK && yukseklik >= EN_AZ_YUKSEKLIK

    /** Ayarlara yazılan biçim: "sol,ust,sag,alt". */
    fun metin(): String = "$sol,$ust,$sag,$alt"

    /** Piksel aralıkları; [pay] ekran boyunun oranı olarak her yana eklenir. */
    fun yatay(w: Int, pay: Float = 0f): IntRange =
        ((sol - pay) * w).toInt().coerceAtLeast(0)..((sag + pay) * w).toInt().coerceAtMost(w)

    fun dikey(h: Int, pay: Float = 0f): IntRange =
        ((ust - pay) * h).toInt().coerceAtLeast(0)..((alt + pay) * h).toInt().coerceAtMost(h)

    companion object {
        /** Bundan dar ya da alçak bir seçim parmağın kaymasıdır, bölge değil. */
        const val EN_AZ_GENISLIK = 0.05f
        const val EN_AZ_YUKSEKLIK = 0.02f

        fun oku(s: String?): EkranBolgesi? {
            val p = s?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return null
            if (p.size != 4) return null
            return EkranBolgesi(p[0], p[1], p[2], p[3]).takeIf { it.gecerli }
        }

        /** İki köşeden (hangi sırayla çizilmiş olursa olsun) ekranın içinde kalan bölge. */
        fun ikiNoktadan(x1: Float, y1: Float, x2: Float, y2: Float): EkranBolgesi = EkranBolgesi(
            minOf(x1, x2).coerceIn(0f, 1f),
            minOf(y1, y2).coerceIn(0f, 1f),
            maxOf(x1, x2).coerceIn(0f, 1f),
            maxOf(y1, y2).coerceIn(0f, 1f)
        )
    }
}
