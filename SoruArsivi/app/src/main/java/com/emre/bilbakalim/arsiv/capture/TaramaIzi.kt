package com.emre.bilbakalim.arsiv.capture

/**
 * Tarama izi — yalnızca teşhis için; biçimi okunaklılığa değil aramaya göre.
 *
 * Neden var: "bot neden bekledi" sorusu şimdiye kadar hep günlük
 * satırlarının arasındaki boşluklardan geriye doğru tahmin edilerek
 * cevaplandı. 20 saniyelik bir takılmanın sebebi (her taramada sessizce
 * geri çeviren bir kapı) ancak ekran görüntüsü ölçülünce bulunabildi. İz,
 * her taramanın **nerede bittiğini** ve **hangi aşamanın ne kadar
 * sürdüğünü** tutuyor:
 *
 *  - `İZ <süre>ms ×N [<çıkış>/<otomatik>×n ...] ort= max=(en yavaşın aşamaları)`
 *    — son satırdan bu yana biten taramalar, nerede bittiklerine göre
 *    sayılmış. Her olay satırından önce ve en geç beş saniyede bir yazılıyor:
 *    günlük boğulmuyor ama sessiz bir döngü de artık görünmez değil.
 *  - `İZ YAVAŞ` — uzun süren tek tarama, aşama aşama.
 *  - `İZ BOŞLUK` — iki tarama arasında uzun ara (tarama hiç çalışmamış).
 *  - `İZ TAKILDI` — süren bir tarama bir aşamada kaldı: kodun nerede
 *    beklediği. Bekçi ayrı bir işte çalıştığı için tarama dönmese de yazılır.
 *
 * Aşama süreleri tur saati gibi ölçülüyor: [Iz.adim] bir önceki aşamayı
 * kapatıp yenisini açıyor. Taramanın koduna her aşamanın başında tek satır
 * ekleniyor, bloklar sarmalanmıyor.
 */
internal class TaramaIzi(
    private val saat: () -> Long,
    /** Günlüğe doğrudan yazar; izin kendi satırları için. */
    private val yaz: (String) -> Unit
) {

    /** Tek bir taramanın izi. */
    class Iz internal constructor(private val saat: () -> Long) {
        val basla: Long = saat()
        /** Şu an hangi aşamada — bekçi takılmayı bununla yazıyor. */
        @Volatile var asama: String = "basla"
            private set
        @Volatile var asamaBasla: Long = basla
            private set
        /** Taramanın nerede bittiği ("kart_kapisi", "durgun", ...). */
        @Volatile var cikis: String = "?"
        /** Çıkışa eşlik eden kısa ayrıntı (red sebebi, paket adı...). */
        @Volatile var ek: String? = null
        /** Otomatik modun bu turdaki kararı ("gecikme", "basiliyor", ...). */
        @Volatile var oto: String = "-"
        internal val sureler = LinkedHashMap<String, Long>()
        @Volatile internal var takildiYazildi = false

        /** Önceki aşamayı kapatır, [ad] aşamasını açar. */
        @Synchronized
        fun adim(ad: String) {
            val now = saat()
            sureler[asama] = (sureler[asama] ?: 0L) + (now - asamaBasla)
            asama = ad
            asamaBasla = now
        }

        /** Çıkışı işaretler; kısa yol: `return iz.cik("durgun")`. */
        fun cik(neden: String, ayrinti: String? = null) {
            cikis = neden
            if (ayrinti != null) ek = ayrinti
        }

        /** Sıfırdan büyük aşamalar: "ocr=180 kutu=22". */
        @Synchronized
        fun asamalar(): String = sureler.entries
            .filter { it.value > 0 }
            .joinToString(" ") { "${it.key}=${it.value}" }
    }

    /** Şu an süren tarama; bekçi okuyor. */
    @Volatile var aktif: Iz? = null
        private set

    private var sonBitis = 0L
    private var sonAnahtar: String? = null

    // Pencere: son yazımdan bu yana biten taramalar. Ardışık aynı çıkışları
    // değil, penceredeki bütün çıkışları sayıyoruz; çünkü taramalar sık sık
    // iki çıkış arasında gidip geliyor ("ocr_erken" ile "ayni_soru" gibi)
    // ve her geçişte satır yazmak günlüğü boğardı.
    private var pencereBasla = 0L
    private var pencereSon = 0L
    private val sayac = LinkedHashMap<String, Int>()
    private val ekler = LinkedHashMap<String, String>()
    private var adet = 0
    private var toplam = 0L
    private var enUzun = -1L
    private var enUzunAnahtar = ""
    private var enUzunAsamalar = ""

    @Synchronized
    fun basla(): Iz {
        val iz = Iz(saat)
        if (sonBitis > 0L && iz.basla - sonBitis >= BOSLUK_MS) {
            bosaltKilitli()
            yaz("İZ BOŞLUK ${iz.basla - sonBitis}ms önceki=$sonAnahtar")
        }
        aktif = iz
        return iz
    }

    @Synchronized
    fun bitir(iz: Iz) {
        iz.adim("son")
        val now = saat()
        if (aktif === iz) aktif = null
        sonBitis = now
        val sure = now - iz.basla
        val anahtar = "${iz.cikis}/${iz.oto}"
        sonAnahtar = anahtar
        if (sure >= YAVAS_MS) {
            yaz("İZ YAVAŞ ${sure}ms $anahtar${iz.ek?.let { " ek=$it" } ?: ""} [${iz.asamalar()}]")
        }
        if (adet == 0) pencereBasla = iz.basla
        pencereSon = now
        adet++
        toplam += sure
        sayac[anahtar] = (sayac[anahtar] ?: 0) + 1
        iz.ek?.let { ekler[anahtar] = it }
        if (sure > enUzun) {
            enUzun = sure
            enUzunAnahtar = anahtar
            enUzunAsamalar = iz.asamalar()
        }
        if (now - pencereBasla >= GRUP_MAX_MS) bosaltKilitli()
    }

    /**
     * Pencereyi yazar. Servis her günlük satırından önce çağırıyor ki
     * satırlar zaman sırasını korusun: bir olaydan önceki taramalar o
     * olaydan önce yazılmış olsun.
     */
    @Synchronized
    fun bosalt() = bosaltKilitli()

    private fun bosaltKilitli() {
        if (adet == 0) return
        // Yalnızca karar turu sırasındaki taramalardan oluşan pencere
        // yazılmıyor: tarama o sırada hemen dönüyor ve turun kendi özeti
        // ("İZ KARAR_TURU") zaten yazılıyor.
        val yazilacak = sayac.keys.any { it.substringBefore('/') !in SESSIZ_CIKISLAR }
        if (yazilacak) {
            yaz(
                "İZ ${pencereSon - pencereBasla}ms ×$adet " +
                    "[${sayac.entries.joinToString(" ") { "${it.key}×${it.value}" }}] " +
                    "ort=${toplam / adet} max=$enUzun($enUzunAnahtar: $enUzunAsamalar)" +
                    (if (ekler.isEmpty()) "" else
                        " ek{${ekler.entries.joinToString(" ") { "${it.key}=${it.value}" }}}")
            )
        }
        sayac.clear()
        ekler.clear()
        adet = 0
        toplam = 0L
        enUzun = -1L
        enUzunAnahtar = ""
        enUzunAsamalar = ""
    }

    /**
     * Bekçi: süren tarama [TAKILMA_MS]'den uzun sürdüyse nerede kaldığını
     * bir kez yazar. Taramadan bağımsız bir işten çağrılmalı; tarama
     * takıldıysa kendi satırını yazamaz.
     */
    fun denetle() {
        val iz = aktif ?: return
        val now = saat()
        if (iz.takildiYazildi || now - iz.basla < TAKILMA_MS) return
        iz.takildiYazildi = true
        yaz(
            "İZ TAKILDI ${now - iz.basla}ms aşama=${iz.asama} " +
                "(${now - iz.asamaBasla}ms) [${iz.asamalar()}]"
        )
    }

    companion object {
        /** Bundan uzun süren tarama aşama aşama yazılır. */
        const val YAVAS_MS = 900L
        /** İki tarama arasında bundan uzun ara "boşluk" sayılır. */
        const val BOSLUK_MS = 2500L
        /** Pencere en geç bu kadar sürede bir yazılır. */
        const val GRUP_MAX_MS = 5000L
        /** Süren tarama bu kadar sürdüyse takılmış sayılır. */
        const val TAKILMA_MS = 4000L
        /** Grubu günlüğe yazılmayan çıkışlar. */
        val SESSIZ_CIKISLAR = setOf("burst")
    }
}
