package com.emre.bilbakalim.arsiv.capture

/**
 * Şıkkın yazısının piksel imzası: metin ayırt edemediğinde şıkları görüntüden
 * ayırmak için.
 *
 * Neden gerekti: "ve" / "veya" bağlacı, "büyüktür" gibi sorularda şıklar
 * sembol (∨ ∧ > <). ML Kit üçünü de «V» okuyor ve arşivde şıklar
 * «V / V / V / <» duruyor. Metin aynı olduğu için doğru cevabın hangi «V»
 * olduğu hiçbir zaman kaydedilemiyor, bot bu sorularda hep rastgele
 * basıyordu. "‰" ile "8" de aynı durumda.
 *
 * İmza: kutunun içindeki koyu (yazı) piksellerin sınırlayıcı kutusu
 * [N]x[N] ızgaraya bölünüp her hücre "yazı var / yok" diye işaretleniyor.
 * Sınırlayıcı kutuya ölçeklendiği için ekran çözünürlüğünden ve yazının
 * boyundan bağımsız; oyun aynı sembolü her seferinde aynı çizdiği için aynı
 * şık neredeyse aynı imzayı veriyor, "<" ile ">" ya da "∨" ile "∧" ise
 * birbirinin aynası.
 *
 * Yalnızca beyaz hapların üstünde ölçülüyor (soru okunduğu anda); renkli
 * haplarda da yazı koyu kaldığı için ölçü bozulmuyor ama buna dayanılmıyor.
 *
 * Android'e bağlı değil: piksel dizisi alıyor, birim testte denenebiliyor.
 */
object SikImzasi {

    /** Izgaranın kenarı: imza N*N bit. */
    const val N = 16

    /** Bu parlaklığın altı yazı. Lacivert yazı ~40, en koyu hap (kırmızı) ~138. */
    private const val YAZI_MAX_PARLAKLIK = 110

    /** Hücrenin yazı sayılması için yazı piksellerinin en az oranı. */
    private const val HUCRE_DOLULUK = 0.25f

    /** Anlamlı bir imza için en az yazı pikseli. */
    private const val EN_AZ_YAZI = 6

    /**
     * Seçim için: en yakın aday bu kadar bitten fazla farklıysa aynı şık değil.
     *
     * Gerçek ekran görüntüleriyle ölçüldü (tablet 1848x2960, telefon
     * 1080x2400; kare %45-70 küçültülüp JPEG'le bozularak, yani başka bir
     * cihazda çizilmiş gibi): aynı şık 10-41 bit, farklı şıklar en az 58 bit
     * ("<" ">" "v" "^" birbirinden 101-160 bit). Aynı cihazda aynı ölçekte
     * alınan karelerde aynı şıkkın farkı bunun çok altında.
     */
    private const val ESLESME_MAX = 56

    /** En yakın ile ikinci en yakın arasında en az bu kadar fark olmalı. */
    private const val EN_AZ_ARALIK = 24

    /**
     * [px] (ARGB, satır satır, [w] x [h]) içindeki yazının imzası; yazı
     * yoksa null. Dönen metin N*N bitin onaltılık hâli.
     */
    fun hesapla(px: IntArray, w: Int, h: Int): String? {
        if (w <= 0 || h <= 0 || px.size < w * h) return null
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var sayi = 0
        for (y in 0 until h) {
            val satir = y * w
            for (x in 0 until w) {
                if (yaziMi(px[satir + x])) {
                    sayi++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (sayi < EN_AZ_YAZI) return null

        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val bitler = BooleanArray(N * N)
        for (gy in 0 until N) {
            val y0 = minY + gy * bh / N
            val y1 = (minY + (gy + 1) * bh / N).coerceAtLeast(y0 + 1)
            for (gx in 0 until N) {
                val x0 = minX + gx * bw / N
                val x1 = (minX + (gx + 1) * bw / N).coerceAtLeast(x0 + 1)
                var yazi = 0
                var toplam = 0
                for (y in y0 until minOf(y1, maxY + 1)) {
                    val satir = y * w
                    for (x in x0 until minOf(x1, maxX + 1)) {
                        toplam++
                        if (yaziMi(px[satir + x])) yazi++
                    }
                }
                bitler[gy * N + gx] = toplam > 0 && yazi >= toplam * HUCRE_DOLULUK
            }
        }
        return onaltilik(bitler)
    }

    /** İki imzanın farklı bit sayısı; biri yoksa ya da boyları tutmuyorsa null. */
    fun mesafe(a: String?, b: String?): Int? {
        if (a.isNullOrEmpty() || b.isNullOrEmpty() || a.length != b.length) return null
        var d = 0
        for (i in a.indices) {
            val x = a[i].digitToIntOrNull(16) ?: return null
            val y = b[i].digitToIntOrNull(16) ?: return null
            d += Integer.bitCount(x xor y)
        }
        return d
    }

    /**
     * [adaylar] arasından [hedef] imzasına açık farkla en yakın olanın
     * [imzalar] içindeki sırası; karar verilemiyorsa null.
     *
     * Açık fark şartı: en yakın aday [ESLESME_MAX] içinde olmalı ve ikinci
     * en yakından en az [EN_AZ_ARALIK] bit ayrışmalı. Tahmin yapılmıyor —
     * yanlış şıkka basmaktansa rastgeleye düşmek aynı sonuç, yanlış cevap
     * yazmak ise daha kötü.
     */
    fun enYakin(hedef: String?, adaylar: List<Int>, imzalar: List<String?>): Int? {
        if (hedef.isNullOrEmpty() || adaylar.isEmpty()) return null
        val olculen = adaylar.mapNotNull { i -> mesafe(hedef, imzalar.getOrNull(i))?.let { i to it } }
        // Adaylardan birinin imzası yoksa karşılaştırma eksik: karar verilmiyor.
        if (olculen.size != adaylar.size) return null
        val sirali = olculen.sortedBy { it.second }
        val (enIyi, d) = sirali[0]
        if (d > ESLESME_MAX) return null
        val ikinci = sirali.getOrNull(1)?.second
        if (ikinci != null && ikinci - d < EN_AZ_ARALIK) return null
        return enIyi
    }

    private fun yaziMi(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        // Yaklaşık algısal parlaklık, tam sayıyla.
        return (r * 299 + g * 587 + b * 114) / 1000 < YAZI_MAX_PARLAKLIK
    }

    private fun onaltilik(bitler: BooleanArray): String = buildString(bitler.size / 4) {
        var i = 0
        while (i < bitler.size) {
            var v = 0
            for (k in 0 until 4) if (bitler.getOrElse(i + k) { false }) v = v or (8 shr k)
            append("0123456789abcdef"[v])
            i += 4
        }
    }
}
