package com.emre.bilbakalim.arsiv.data

import com.emre.bilbakalim.arsiv.util.TurkishText

/**
 * Ana ekrandaki "Kaydedilecek kategori" listesi ve adı değiştirilmiş
 * kategorilerin eski adları.
 *
 * Eski adlar neden tutuluyor: oyun kategoriyi ekranda kendi adıyla
 * gösteriyor. "Din Kültürü"nü "Bilim" yaptıktan sonra ekranda yine "Din
 * Kültürü" okunuyor; ad tanıma o yazıyı "Bilim"e çevirmezse eski kategori
 * ilk yeni soruyla arşive geri dönerdi. Aynı şey eski bir yedeği içe
 * aktarırken de geçerli.
 *
 * Adlar karşılaştırılırken [TurkishText.normalizeKey] kullanılıyor: "bilim",
 * "Bilim" ve "BİLİM" aynı kategori sayılıyor.
 *
 * Android'e bağlı değil; birim testte doğrudan denenebiliyor.
 */
data class KategoriListesi(
    val adlar: List<String>,
    /** Eski adın anahtarı → bugünkü ad. */
    val eskiAdlar: Map<String, String> = emptyMap()
) {

    /** Anahtar → listedeki ad. Ad tanıma her ayrıştırmada bakıyor; bir kez kuruluyor. */
    private val adAnahtarlari: Map<String, String> by lazy {
        val m = HashMap<String, String>(adlar.size * 2)
        for (ad in adlar) m.putIfAbsent(anahtar(ad), ad)
        m
    }

    /** Listede aynı adı taşıyan kategori (yazılışı farklı olabilir). */
    fun bul(ad: String): String? {
        val k = anahtar(ad)
        if (k.isEmpty()) return null
        return adAnahtarlari[k]
    }

    /**
     * Bir kategori adını bugünkü adına çevirir: listede varsa listedeki
     * yazılışı, adı değiştirilmişse yeni adı, ikisi de değilse kendisini.
     */
    fun guncelAd(ad: String?): String? {
        val temiz = ad?.let { sadelestir(it) }?.takeIf { it.isNotEmpty() } ?: return null
        return bul(temiz) ?: eskiAdlar[anahtar(temiz)] ?: temiz
    }

    /**
     * Ekranda okunan bir yazı bilinen bir kategori mi? Yalnızca yazının
     * tamamı bir kategori adıyla (ya da eski adıyla) aynıysa.
     */
    fun tani(yazi: String): String? {
        val k = anahtar(yazi)
        if (k.isEmpty()) return null
        return adAnahtarlari[k] ?: eskiAdlar[k]
    }

    /**
     * Listeye yeni bir kategori ekler. Aynı ad zaten varsa liste değişmez.
     *
     * Adı değiştirilmiş bir kategorinin eski adı yeniden eklenirse o ad
     * artık yönlendirilmiyor: kullanıcı onu yeniden ayrı bir kategori
     * olarak istiyor.
     */
    fun ekle(ad: String): KategoriListesi {
        val temiz = sadelestir(ad)
        if (temiz.isEmpty() || bul(temiz) != null) return this
        return KategoriListesi(adlar + temiz, eskiAdlar - anahtar(temiz))
    }

    /** [yenidenAdlandir] sonucu. */
    data class AdDegisimi(
        val liste: KategoriListesi,
        /** Kayıtların alacağı ad. Başka bir kategoriyle birleştiyse onun adı. */
        val hedef: String,
        /** [hedef] listede zaten başka bir kategori olarak duruyordu. */
        val birlesti: Boolean
    )

    /**
     * [eski] kategorisinin adını [yeni] yapar.
     *
     * [yeni] listede zaten başka bir kategori olarak varsa ikisi birleşir:
     * eski ad listeden kalkar, kayıtlar var olan kategoriye geçer. Yalnızca
     * yazılışı değişiyorsa ("bilim" → "Bilim") bu bir birleşme değil.
     *
     * [eski] listede değilse (kategori yalnızca arşivde, ör. elle yazılmış
     * ya da yedekten gelmiş) yeni ad listenin sonuna eklenir.
     *
     * Yeni ad boşsa ya da hiçbir şey değişmiyorsa null.
     */
    fun yenidenAdlandir(eski: String, yeni: String): AdDegisimi? {
        val eskiTemiz = sadelestir(eski)
        val yeniTemiz = sadelestir(yeni)
        if (eskiTemiz.isEmpty() || yeniTemiz.isEmpty() || eskiTemiz == yeniTemiz) return null
        val eskiK = anahtar(eskiTemiz)
        val yeniK = anahtar(yeniTemiz)
        if (eskiK.isEmpty() || yeniK.isEmpty()) return null

        val ayniKategori = eskiK == yeniK
        val varOlan = if (ayniKategori) null else bul(yeniTemiz)
        val hedef = varOlan ?: yeniTemiz

        val yeniAdlar = ArrayList<String>(adlar.size + 1)
        var yerlesti = varOlan != null
        for (ad in adlar) {
            if (anahtar(ad) == eskiK) {
                // Eski adın yerine yenisi; birleşmede eski ad listeden kalkıyor.
                if (!yerlesti) {
                    yeniAdlar.add(hedef)
                    yerlesti = true
                }
            } else {
                yeniAdlar.add(ad)
            }
        }
        if (!yerlesti) yeniAdlar.add(hedef)

        val hedefK = anahtar(hedef)
        val yeniEskiAdlar = LinkedHashMap<String, String>()
        for ((k, v) in eskiAdlar) {
            // Eski ada yönlenen adlar artık yeni ada yönleniyor.
            yeniEskiAdlar[k] = if (anahtar(v) == eskiK) hedef else v
        }
        if (!ayniKategori) yeniEskiAdlar[eskiK] = hedef
        // Hedef ad yaşayan bir kategori; başka bir yere yönlenmemeli.
        yeniEskiAdlar.remove(hedefK)

        return AdDegisimi(KategoriListesi(yeniAdlar, yeniEskiAdlar), hedef, varOlan != null)
    }

    companion object {
        /** Karşılaştırma anahtarı: büyük/küçük harf, Türkçe harfler ve boşluklar yok sayılır. */
        fun anahtar(ad: String): String = TurkishText.normalizeKey(ad)

        /**
         * Baştaki/sondaki boşluklar atılır, aradaki boşluklar teke iner.
         * Satır sonu ve sekme de boşluk sayılıyor; adlar ayarlarda satır
         * satır tutuluyor.
         */
        fun sadelestir(ad: String): String = ad.trim().replace(BOSLUK, " ")

        private val BOSLUK = Regex("\\s+")
    }
}
