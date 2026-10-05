package com.emre.bilbakalim.arsiv.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.Repo
import com.emre.bilbakalim.arsiv.util.TurkishText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Oyun uzakta" modu: oyunu başkası oynuyor, sen yayınını bu telefonda
 * izliyorsun (YouTube, Twitch, Kick… hangisi olursa). Yayının içindeki
 * soruyu okuyup arşivde arıyor; cevap biliniyorsa doğru şıkkın yanına ok
 * koyuyor, bilinmiyorsa bunu açıkça gösteriyor. Rastgele tahmin yok;
 * "bilinmeyen soruyu yapay zekâya sor" açıksa bilinmeyen soru Groq /
 * Gemini'ye soruluyor ve onun seçtiği şık **mor** okla, arşivden bilinen
 * cevabın yeşil okundan ayrı gösteriliyor.
 *
 * Yerel moddan üç farkı var, üçü de bilerek:
 *  • **Ekrana dokunulmuyor.** Hiçbir jest gönderilmiyor.
 *  • **Arşive yazılmıyor.** Arama salt okunur ([Repo.saltOkunurBul]):
 *    kayıt eklenmiyor, sayaç artmıyor, cevap yazılmıyor. Yayın görüntüsü
 *    bulanık; arşivi kirletmesin.
 *  • **Hangi uygulamanın önde olduğuna bakılmıyor.** Yayın her uygulamada
 *    olabilir. Yalnızca kendi uygulamamız öndeyken durup katmanı gizliyor.
 *
 * Oyunun yeri yayına göre değiştiği için soru ve şık bölgesi yüzen
 * düğmeyle, dondurulmuş bir kare üstünde elle seçiliyor.
 */
class UzakIzleyici(
    private val servis: AccessibilityService,
    private val prefs: Prefs,
    private val repo: Repo,
    private val ekranGoruntusu: suspend () -> Bitmap?,
    private val ekranBoyu: () -> Pair<Int, Int>,
    /** Servisle ortak: kota cezaları ve anahtar dönüşümü paylaşılıyor. */
    private val yapayZeka: YapayZeka,
    private val log: (String) -> Unit
) : UzakKatman.Olaylar {

    /** Ekranda gösterilen durum. */
    sealed interface Durum {
        data object BolgeYok : Durum
        data object Duraklatildi : Durum
        /** Soru okunamıyor (soru ekranı yok, geçiş, bulanık kare). */
        data object Bekliyor : Durum
        /** Cevap biliniyor ve ekrandaki şıklardan biri. */
        data class Bilinen(val sira: Int, val metin: String) : Durum
        /** Cevap biliniyor ama ekrandaki şıklarda bulunamadı: metni gösteriliyor. */
        data class Eslesmedi(val metin: String) : Durum
        /** Soru arşivde var, doğru cevabı henüz yok. */
        data object CevapYok : Durum
        data object ArsivdeYok : Durum
        /** Arşivde cevabı yok, yapay zekâya soruluyor. */
        data object Soruluyor : Durum
        /**
         * Yapay zekânın tahmini; [kaynak] "Groq 1" gibi. [eminDegil]: model
         * güveninin ayardaki eşiğin altında olduğunu söyledi (soluk ok).
         */
        data class Tahmin(
            val sira: Int,
            val metin: String,
            val kaynak: String,
            val guven: Int? = null,
            val eminDegil: Boolean = false
        ) : Durum
    }

    /**
     * Yapay zekânın bir soruya verdiği cevap, şık **metniyle**: oyun şıkları
     * her turda karıştırıyor, sıra saklanırsa sonraki turda yanlış şıkkı
     * gösterirdi. Anahtar parmak izi (şıklar sıralı), yani karışmış sıra
     * aynı cevabı buluyor ve kota boşa harcanmıyor. null: sorulamadı.
     */
    private val aiCevaplari = LinkedHashMap<String, String?>()
    /** Şu an yapay zekâya sorulan sorunun parmak izi (aynı anda tek istek). */
    @Volatile private var aiSorulan: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dongu: Job? = null
    private var katman: UzakKatman? = null

    @Volatile private var secimSuruyor = false
    @Volatile private var durum: Durum? = null
    @Volatile private var sonOkumaAt = 0L
    @Volatile private var sonRed: String? = null
    /** Son okumaların arama sonucu (yalnızca döngüden erişiliyor). */
    private val onbellek = LinkedHashMap<String, Durum>()

    private fun onbellegeYaz(anahtar: String, d: Durum) {
        onbellek[anahtar] = d
        while (onbellek.size > ONBELLEK) onbellek.remove(onbellek.keys.first())
    }

    val calisiyor: Boolean get() = dongu?.isActive == true

    fun baslat() {
        if (calisiyor) return
        secimSuruyor = false
        durum = null
        dongu = scope.launch {
            // Arşiv mod kapalıyken değişmiş olabilir (yerel modda öğrenilen
            // cevaplar, içe aktarma).
            onbellek.clear()
            withContext(Dispatchers.Main) { katmaniKur() }
            log("UZAK MOD açıldı: yayındaki soru okunacak, ekrana dokunulmayacak, arşive yazılmayacak")
            try {
                calis()
            } catch (t: Throwable) {
                if (t !is CancellationException) log("HATA uzak mod: ${t.javaClass.simpleName}: ${t.message}")
                throw t
            }
        }
    }

    fun durdur() {
        val vardi = dongu != null
        dongu?.cancel()
        dongu = null
        scope.launch(Dispatchers.Main) {
            katman?.kaldir()
            katman = null
        }
        if (vardi) log("uzak mod kapandı")
    }

    /** Servis kapanıyor. */
    fun kapat() {
        dongu?.cancel()
        dongu = null
        runCatching { katman?.kaldir() }
        katman = null
        scope.cancel()
    }

    private fun katmaniKur() {
        if (katman != null) return
        val k = UzakKatman(servis, this)
        val (w, h) = ekranBoyu()
        val s = prefs.state.value
        val x = s.uzakDugmeX?.let { (it * w).toInt() } ?: k.dp(12f)
        val y = s.uzakDugmeY?.let { (it * h).toInt() } ?: (h * VARSAYILAN_DUGME_Y).toInt()
        k.dugmeKur(x, y)
        katman = k
        goster(Durum.BolgeYok, null)
    }

    // --- Döngü -------------------------------------------------------------

    private suspend fun calis() {
        while (scope.isActive) {
            delay(DONGU_MS)
            if (secimSuruyor) continue
            val s = prefs.state.value
            if (s.paused) {
                gosterAna(Durum.Duraklatildi, null)
                continue
            }

            // Kendi uygulamamız öndeyse (ayar yapıyorsun) okumanın anlamı
            // yok; katman da uygulamanın üstünü kapatmasın. Android dokunulan
            // pencereyi de "etkin" sayıyor: düğmeyi sürüklerken etkin pencere
            // bizim katmanımız oluyor ve paketi bizim. O katman sayılmıyor,
            // yoksa düğme sürüklenirken kaybolurdu.
            val bizim = withContext(Dispatchers.Main) {
                runCatching {
                    val kok = servis.rootInActiveWindow
                    kok?.packageName?.toString() == servis.packageName &&
                        kok?.window?.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                }.getOrDefault(false)
            }
            withContext(Dispatchers.Main) { katman?.gizle(bizim) }
            if (bizim) continue

            val soru = s.uzakSoruBolgesi
            val sik = s.uzakSikBolgesi
            if (soru == null || sik == null) {
                gosterAna(Durum.BolgeYok, null)
                continue
            }

            val kare = ekranGoruntusu() ?: continue
            val sonuc = try {
                UzakOkuyucu.oku(kare, soru, sik, s)
            } finally {
                if (!kare.isRecycled) kare.recycle()
            }

            val okuma = sonuc.okuma
            val simdi = SystemClock.uptimeMillis()
            if (okuma == null) {
                if (sonuc.red != sonRed) {
                    sonRed = sonuc.red
                    log("uzak · okunamadı: ${sonuc.red}")
                }
                // Yayın karesi bir an bozulabilir (sıkıştırma, cevap
                // animasyonu); işaret hemen sönmesin.
                if (simdi - sonOkumaAt > TUTMA_MS) {
                    gosterAna(Durum.Bekliyor, null)
                }
                continue
            }
            sonRed = null
            sonOkumaAt = simdi

            // Bulanık yayında aynı soru kareden kareye bir harf farklı
            // okunabiliyor; her okuma için arşivi baştan taramamak ve günlüğü
            // doldurmamak için son okumaların sonucu saklanıyor.
            // Parmak izi şıkları sıralayarak hesaplıyor; oyun ise şıkları her
            // turda karıştırıyor. Saklanan sonuç bir şık sırası içerdiği için
            // anahtarda ekrandaki sıra da var.
            val anahtar = TurkishText.fingerprint(okuma.soru, okuma.siklar) + "|" +
                okuma.siklar.joinToString("|")
            val bilinen = onbellek[anahtar]
            val yeni = bilinen ?: ara(okuma).also { onbellegeYaz(anahtar, it) }
            if (bilinen == null) {
                log(
                    "UZAK SORU «${okuma.soru.take(70)}» · " +
                        okuma.siklar.mapIndexed { i, o -> "${'A' + i}«${o.take(20)}»" }.joinToString(" ") +
                        " · ${if (okuma.kutuYolu) "kutu" else "metin"} → ${ozet(yeni)}"
                )
                // Yayındaki soru bizde yok: yerel moddaki gibi sesle haber ver.
                if ((yeni == Durum.ArsivdeYok || yeni == Durum.CevapYok) && s.unknownChime) {
                    Chime.play(servis)?.let { log("SES ÇALINAMADI: $it") }
                }
            }
            gosterAna(yapayZekaDurumu(yeni, okuma, s), okuma)
        }
    }

    /**
     * Cevabı bilinmeyen soruda yapay zekânın durumu: cevabı geldiyse
     * tahmin, soruluyorsa "soruluyor", sorulamıyorsa arşivin durumu.
     * Soru gerekiyorsa arka planda sorulur; döngü beklemez, cevap gelince
     * sonraki turda gösterilir.
     */
    private fun yapayZekaDurumu(d: Durum, o: UzakOkuyucu.Okuma, s: Prefs.Settings): Durum {
        if (d != Durum.ArsivdeYok && d != Durum.CevapYok) return d
        if (!s.aiWhenUnknown) return d
        if (s.groqKeys.all { it.isBlank() } && s.geminiKeys.all { it.isBlank() }) return d
        if (o.siklar.size < 2) return d
        val iz = TurkishText.fingerprint(o.soru, o.siklar)
        val (soruldu, cevap, kaynak) = synchronized(aiCevaplari) {
            Triple(aiCevaplari.containsKey(iz), aiCevaplari[iz], aiKaynaklari[iz])
        }
        val guven = synchronized(aiCevaplari) { aiGuvenleri[iz] }
        if (soruldu) {
            val metin = cevap ?: return d
            val hedef = TurkishText.normalizeKey(metin)
            val sira = o.siklar.indices.singleOrNull { TurkishText.normalizeKey(o.siklar[it]) == hedef }
                ?: return d
            return Durum.Tahmin(
                sira, o.siklar[sira], kaynak ?: "?", guven,
                eminDegil = guven != null && guven < s.aiGuvenEsigi
            )
        }
        if (aiSorulan == null) {
            aiSorulan = iz
            val soru = o.soru
            val siklar = o.siklar.toList()
            scope.launch {
                val r = runCatching { yapayZeka.sor(soru, siklar, s.groqKeys, s.geminiKeys) }.getOrNull()
                val i = r?.index
                synchronized(aiCevaplari) {
                    aiCevaplari[iz] = i?.let { siklar[it] }
                    r?.anahtar?.let { aiKaynaklari[iz] = it.ad }
                    r?.guven?.let { aiGuvenleri[iz] = it }
                    while (aiCevaplari.size > ONBELLEK) {
                        val ilk = aiCevaplari.keys.first()
                        aiCevaplari.remove(ilk); aiKaynaklari.remove(ilk); aiGuvenleri.remove(ilk)
                    }
                }
                val once = r?.hatalar?.takeIf { it.isNotEmpty() }?.let { " · önce: " + it.joinToString("; ") } ?: ""
                log(
                    if (i != null) "UZAK YAPAY ZEKÂ → ${'A' + i} «${siklar[i].take(28)}» · " +
                        (r?.guven?.let { "%$it emin · " } ?: "") +
                        "${r?.anahtar?.ad} ${r?.model} · ${r?.sureMs} ms$once"
                    else "UZAK YAPAY ZEKÂ: cevap alınamadı$once"
                )
                aiSorulan = null
            }
        }
        return if (aiSorulan == iz) Durum.Soruluyor else d
    }

    private val aiKaynaklari = HashMap<String, String>()
    private val aiGuvenleri = HashMap<String, Int>()

    /** Arşivde arar; hiçbir şey yazmaz. */
    private suspend fun ara(o: UzakOkuyucu.Okuma): Durum {
        val kayit = runCatching { repo.saltOkunurBul(o.soru, o.siklar) }.getOrNull()
            ?: return Durum.ArsivdeYok
        if (kayit.correctIndex == null) return Durum.CevapYok
        return when (val b = runCatching { repo.knownAnswerOnScreen(kayit.id, o.siklar) }
            .getOrDefault(Repo.KnownAnswer.None)) {
            is Repo.KnownAnswer.OnScreen -> Durum.Bilinen(b.index, o.siklar.getOrNull(b.index).orEmpty())
            is Repo.KnownAnswer.Unmatched ->
                b.text?.let { Durum.Eslesmedi(it) } ?: Durum.CevapYok
            Repo.KnownAnswer.None -> Durum.CevapYok
        }
    }

    private suspend fun gosterAna(d: Durum, o: UzakOkuyucu.Okuma?) =
        withContext(Dispatchers.Main) { goster(d, o) }

    /** Ana iş parçacığında. */
    private fun goster(d: Durum, o: UzakOkuyucu.Okuma?) {
        durum = d
        val k = katman ?: return
        val (metin, ton) = gorunum(d)
        k.dugmeMetni(metin, ton)

        val s = prefs.state.value
        val soru = s.uzakSoruBolgesi
        val sik = s.uzakSikBolgesi
        val (w, h) = ekranBoyu()
        val cerceve = if (soru != null && sik != null) UzakGeometri.cerceve(soru, sik, w, h) else null

        // Düğme metni uzayınca okunan çerçeveye taşabilir; ölçülünce bak.
        if (cerceve != null) k.dugmeOlculunce { dugmeyiDisariIt(cerceve, w, h) }

        if (cerceve == null || soru == null) {
            k.isaretGizle()
            return
        }
        val okW = k.dp(OK_GENISLIK)
        when (d) {
            is Durum.Bilinen, is Durum.Tahmin -> {
                val sira = if (d is Durum.Bilinen) d.sira else (d as Durum.Tahmin).sira
                val kutu = o?.sikKutulari?.getOrNull(sira)
                if (kutu == null) { k.isaretGizle(); return }
                val hedef = UzakGeometri.piksel(kutu, w, h)
                val okH = hedef.h.coerceIn(k.dp(28f), k.dp(64f))
                val yer = UzakGeometri.isaretYeri(hedef, cerceve, w, okW, okH)
                val renk = when {
                    d !is Durum.Tahmin -> null
                    d.eminDegil -> SOLUK_MOR
                    else -> MOR
                }
                if (yer == null) k.isaretGizle()
                else k.isaretGoster(yer, UzakKatman.Imge.Ok(yer.sagaBakar, renk))
            }
            Durum.ArsivdeYok, Durum.CevapYok, Durum.Soruluyor, is Durum.Eslesmedi -> {
                val hedef = UzakGeometri.piksel(soru, w, h)
                val r = k.dp(ROZET)
                val yer = UzakGeometri.isaretYeri(hedef, cerceve, w, r, r)
                val (yazi, renk) = when (d) {
                    Durum.ArsivdeYok -> "✗" to KIRMIZI
                    Durum.CevapYok -> "?" to TURUNCU
                    Durum.Soruluyor -> "…" to MOR
                    else -> "!" to TURUNCU
                }
                if (yer == null) k.isaretGizle()
                else k.isaretGoster(yer, UzakKatman.Imge.Rozet(yazi, renk))
            }
            else -> k.isaretGizle()
        }
    }

    private fun dugmeyiDisariIt(cerceve: UzakGeometri.Kutu, w: Int, h: Int) {
        val k = katman ?: return
        val kutu = k.dugmeKutusu() ?: return
        val (x, y) = UzakGeometri.disariIt(kutu, cerceve, w, h)
        if (x != kutu.sol || y != kutu.ust) {
            k.dugmeTasi(x, y)
            prefs.setUzakDugme(x / w.toFloat(), y / h.toFloat())
        }
    }

    // --- Düğme olayları (ana iş parçacığı) -----------------------------------

    override fun dugmeBirakildi(x: Int, y: Int) {
        val (w, h) = ekranBoyu()
        prefs.setUzakDugme(x / w.toFloat(), y / h.toFloat())
        val s = prefs.state.value
        val soru = s.uzakSoruBolgesi
        val sik = s.uzakSikBolgesi
        if (soru != null && sik != null) dugmeyiDisariIt(UzakGeometri.cerceve(soru, sik, w, h), w, h)
    }

    override fun dugmeyeBasildi() {
        if (secimSuruyor) return
        secimSuruyor = true
        val k = katman ?: run { secimSuruyor = false; return }
        k.gizle(true)
        scope.launch {
            // Katman ekrandan kalksın; yoksa düğme ve ok karede görünür.
            delay(GIZLEME_MS)
            var kare: Bitmap? = null
            for (deneme in 0 until 3) {
                kare = ekranGoruntusu()
                if (kare != null) break
                delay(GIZLEME_MS)
            }
            withContext(Dispatchers.Main) {
                val katmanSimdi = katman
                if (kare == null || katmanSimdi == null) {
                    secimSuruyor = false
                    katmanSimdi?.gizle(false)
                    Toast.makeText(servis, "Ekran görüntüsü alınamadı", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val s = prefs.state.value
                val (w, h) = ekranBoyu()
                katmanSimdi.secimGoster(
                    kare, w, h, s.uzakSoruBolgesi, s.uzakSikBolgesi,
                    kutuBul = { soru, sik, sonuc ->
                        scope.launch {
                            val bulunan = runCatching { UzakOkuyucu.kutular(kare, soru, sik) }
                                .getOrDefault(emptyList())
                            sonuc(bulunan)
                        }
                    },
                    bitti = { soru, sik -> secimBitti(kare, soru, sik) }
                )
            }
        }
    }

    private fun secimBitti(kare: Bitmap, soru: EkranBolgesi?, sik: EkranBolgesi?) {
        if (soru != null && sik != null) {
            prefs.setUzakBolgeleri(soru, sik)
            log("uzak · bölgeler seçildi: soru ${soru.metin()} · şıklar ${sik.metin()}")
            val (w, h) = ekranBoyu()
            dugmeyiDisariIt(UzakGeometri.cerceve(soru, sik, w, h), w, h)
        }
        katman?.gizle(false)
        secimSuruyor = false
        // Önizleme işi bitmiş olmayabilir; kare onunla paylaşılıyor.
        scope.launch {
            delay(KARE_BIRAKMA_MS)
            if (!kare.isRecycled) kare.recycle()
        }
    }

    companion object {
        /** Düğmenin metni ve rengi. Android'e bağlı değil; testte denenebiliyor. */
        fun gorunum(d: Durum): Pair<String, UzakKatman.Ton> = when (d) {
            Durum.BolgeYok -> "◎ Bölge seç (dokun)" to UzakKatman.Ton.MAVI
            Durum.Duraklatildi -> "⏸ Duraklatıldı" to UzakKatman.Ton.GRI
            Durum.Bekliyor -> "◎ Soru bekleniyor" to UzakKatman.Ton.GRI
            is Durum.Bilinen -> "✓ ${'A' + d.sira} · ${d.metin.take(24)}" to UzakKatman.Ton.YESIL
            is Durum.Eslesmedi -> "✓ Cevap: ${d.metin.take(24)}" to UzakKatman.Ton.YESIL
            Durum.CevapYok -> "? Arşivde var, cevabı yok" to UzakKatman.Ton.TURUNCU
            Durum.ArsivdeYok -> "✗ Arşivde yok" to UzakKatman.Ton.KIRMIZI
            Durum.Soruluyor -> "… Yapay zekâya soruluyor" to UzakKatman.Ton.MOR
            is Durum.Tahmin ->
                (if (d.eminDegil) "🤖? " else "🤖 ") + "${'A' + d.sira} · ${d.metin.take(20)} (" +
                    d.kaynak + (d.guven?.let { ", %$it" } ?: "") + ")" to UzakKatman.Ton.MOR
        }

        private fun ozet(d: Durum): String = when (d) {
            is Durum.Bilinen -> "BİLİNİYOR ${'A' + d.sira} «${d.metin.take(30)}»"
            is Durum.Eslesmedi -> "BİLİNİYOR ama şıklarda yok: «${d.metin.take(30)}»"
            Durum.CevapYok -> "arşivde var, cevabı yok"
            Durum.ArsivdeYok -> "ARŞİVDE YOK"
            is Durum.Tahmin -> "YAPAY ZEKÂ ${'A' + d.sira} «${d.metin.take(30)}»"
            else -> d.toString()
        }

        /** Döngünün bekleme aralığı; ekran görüntüsünün kendi sınırı ayrıca var. */
        private const val DONGU_MS = 250L
        /** Okunamayan karelerde işaret bu kadar süre yerinde kalır. */
        private const val TUTMA_MS = 2500L
        private const val GIZLEME_MS = 250L
        private const val KARE_BIRAKMA_MS = 3000L
        private const val VARSAYILAN_DUGME_Y = 0.12f
        private const val OK_GENISLIK = 52f
        private const val ROZET = 40f
        private const val ONBELLEK = 32
        private val KIRMIZI = 0xFFC62828.toInt()
        private val TURUNCU = 0xFFEF6C00.toInt()
        private val MOR = 0xFF6A1B9A.toInt()
        /** Emin olmadığı tahmin: aynı mor, soluk. */
        private val SOLUK_MOR = 0x996A1B9A.toInt()
    }
}
