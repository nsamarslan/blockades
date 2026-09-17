package com.emre.bilbakalim.arsiv.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.emre.bilbakalim.arsiv.ArsivApp
import com.emre.bilbakalim.arsiv.MainActivity
import com.emre.bilbakalim.arsiv.R
import com.emre.bilbakalim.arsiv.data.CaptureSource
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.Repo
import com.emre.bilbakalim.arsiv.util.TurkishText
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Uygulamanın motoru.
 *
 * Seçilen uygulamada ekran her değiştiğinde:
 *   1. Erişilebilirlik ağacından metinleri okur (hızlı, hatasız),
 *   2. Yetersizse ekran görüntüsü alıp OCR'a düşer,
 *   3. Soru + şıkları ayrıştırıp veritabanına yazar,
 *   4. Cevap verilince yeşile dönen şıkkı doğru cevap olarak işaretler.
 *
 * İki mod var:
 *
 *  • **Manuel** (varsayılan): oyunu sen oynarsın. Servis ekrana hiçbir şekilde
 *    dokunmaz — jest göndermez, tıklama yapmaz, hedef uygulamayı yönlendirmez.
 *    Sadece görüneni okur ve kaydeder.
 *
 *  • **Otomatik**: oyunu [AutoPlayer] oynar. Soru geldiğinde şıklardan birini
 *    rastgele seçip dokunur, tur bitince "Tekrar Oyna" düğmesine basar.
 *    Okuma tarafı hiç değişmez; cevabı yine renkten anlarız, çünkü dokunuşun
 *    kimden geldiğinin ekrandaki renkler açısından bir önemi yok.
 */
class CaptureAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var prefs: Prefs
    private lateinit var repo: Repo
    private lateinit var auto: AutoPlayer

    @Volatile private var lastKey: String? = null
    @Volatile private var lastShotAt = 0L
    @Volatile private var lastOcrAt = 0L
    @Volatile private var lastLoggedFront: String? = null
    @Volatile private var currentEncounterId = -1L
    @Volatile private var lastAnsweredId = -1L
    @Volatile private var lastAnsweredAt = 0L
    @Volatile private var dirty = false
    @Volatile private var pollJob: Job? = null
    @Volatile private var autoJob: Job? = null
    @Volatile private var lastFrameSig: IntArray? = null
    /** Ekranın en son ne zaman değiştiği — "kaç saniyedir kıpırdamıyor" için. */
    @Volatile private var lastFrameChangeAt = 0L
    /** Kıpırdamayan ekranda zorla yapılan son tam tarama. */
    @Volatile private var lastForcedScanAt = 0L
    /** Yakın plan OCR'ın denendiği kare; aynı kare için ikinci kez denenmez. */
    @Volatile private var closeUpSig: IntArray? = null
    /** Son tam ekran OCR sonucu — yakın plan okumasıyla birleştirmek için. */
    @Volatile private var lastOcrItems: List<TextItem> = emptyList()
    @Volatile private var lastAutoIdleLogAt = 0L
    @Volatile private var lastAutoIdleReason: String? = null
    /** Ekranda en son ne zaman gerçek bir soru görüldü (otomatik mod için). */
    @Volatile private var lastQuestionAt = 0L
    /** Tur sonu ekranında en son hangi yazıları gördük — günlük tekrarı olmasın. */
    @Volatile private var lastIdleScreen: String? = null
    /**
     * Dört şıkkıyla birlikte kaydedilmiş sorunun ekrandaki sıra numarası.
     *
     * Soru bir kez düzgün okunduktan sonra ekran değişmeye devam ediyor:
     * cevap açılıyor, şıklar renk değiştiriyor, üstlerine puan balonu
     * düşüyor. Bunlar yeni bir soru değil — ama metin değiştiği için
     * parmak izi de değişiyor ve uygulama yeni soru sanıp bozuk bir kayıt
     * daha açıyordu. Numara artana kadar bu soruyu kilitli tutuyoruz.
     */
    @Volatile private var lockedNumber: Int? = null
    @Volatile private var lockedQuestion: String = ""
    /** Bir kez görülmüş ama henüz doğrulanmamış okuma (kararlılık kapısı). */
    @Volatile private var confirmKey: String? = null
    /**
     * Cevabı açılmayı bekleyen soru.
     *
     * [pendingIndex]: yeşil bantta görülen ama henüz onaylanmamış şık.
     * Dokunduğun anda senin şıkkın da bu banda girdiği için tek bir
     * okumaya güvenilemez — aynı şıkkı iki ardışık örnekte görmek gerekir.
     */
    private data class PendingAnswer(
        val id: Long,
        /**
         * Şıkların ekrandaki kutuları.
         *
         * Değişebilir: şıklar animasyonla yerine oturduğu için ilk okumadaki
         * konumlar birkaç yüz milisaniye sonra kaymış olabiliyor. Aynı soru
         * yeniden okunduğunda tazeleniyor ki hem renk ölçümü hem de otomatik
         * dokunuş güncel konuma baksın.
         */
        var rects: List<Rect>,
        /**
         * Şıkların **ekrandaki** metinleri, [rects] ile aynı sırada.
         *
         * Oyun şıkları her turda karıştırdığı için "doğru cevap 2. şık"
         * bilgisi tek başına bir işe yaramıyor: hem kayda doğru cevabı
         * yazarken hem de otomatik modda bilinen cevaba dokunurken metni
         * eşleştirmek gerekiyor.
         *
         * **var olmak zorunda.** Oyun şıkların sırasını değiştirdiğinde
         * [rects] tek başına tazelenirse "2. metin" ile "2. kutu" başka
         * şıklara ait olur; bot bir şıkka basıp oyun başkasında tepki verir
         * ve arşive yanlış cevap yazılır. İkisi her zaman birlikte
         * güncellenmeli.
         */
        var options: List<String>,
        /**
         * Soru ekrana ne zaman geldi. Otomatik dokunuş hem bu süreyi bekler
         * hem de bu değeri karşılaşmanın kimliği olarak kullanır: aynı soru
         * sonraki turda yeniden çıktığında veritabanı kimliği aynı kalır ama
         * bu damga değişir, böylece yeniden cevaplanır.
         */
        var bornAt: Long = SystemClock.uptimeMillis(),
        /** Kararın yeşili hangi şıkta ve ne zamandan beri görülüyor. */
        var greenIndex: Int? = null,
        var greenSince: Long = 0L,
        /** Dokunuş turkuazı — karar açılmazsa geri düşüş için. */
        var pendingIndex: Int? = null,
        var pendingSince: Long = 0L,
        /** Günlüğe aynı deseni tekrar tekrar yazmamak için. */
        var lastSummary: String? = null,
        /** Atlanan karar satırı da tekrarlanmasın. */
        var lastSkip: String? = null,
        /**
         * Bu soruda hiç renkli şık görüldü mü (turkuaz/yeşil/kırmızı)?
         *
         * "Dokunuş yutuldu mu" kararı buna bakıyor. lastSummary'ye bakmak
         * yanlış olurdu: o, renk bulunamadığında ölçülen ham renkleri de
         * yazıyor ve dokunuşa hiç tepki gelmese bile doluyor.
         */
        var tintSeen: Boolean = false,
        /**
         * Şık yerleşiminin sürümü; oyun şıkları karıştırdıkça artar.
         *
         * [bornAt] artık değişebilir olduğu için "bu hâlâ aynı soru mu"
         * kontrolü tek başına yetmiyor: aynı nesne üzerinde metinler ve
         * kutular değiştiğinde damga da değiştiği hâlde karşılaştırma hep
         * eşit çıkıyor. Dokunuş ile kayıt arasında geçen sürede şıklar
         * karışırsa, elimizdeki kutu artık başka bir metne ait olur. Bu
         * sayaç o aralığı yakalıyor.
         */
        var layout: Int = 0,
        /** Otomatik modda bu soruya kaç kez dokunuldu ve en son ne zaman. */
        var taps: Int = 0,
        var lastTapAt: Long = 0L,
        /** Seçilen şık — yeniden denemelerde aynısına basılır. */
        var chosenIndex: Int? = null,
        /**
         * Bu soruya dokunan uygulamanın kendisi mi (otomatik mod)?
         *
         * Önemli, çünkü "dokunduğun şık öyle kaldı, demek doğru bildin"
         * geri düşüşü elle oynayan biri için makul ama rastgele dokunan bir
         * bot için dörtte üç ihtimalle yanlış — ve o yanlışı arşive doğru
         * cevap diye yazıyor.
         */
        var autoTapped: Boolean = false,
        /** Arşivin doğru cevap dediği şıkkın ekrandaki sırası, biliniyorsa. */
        var knownIndex: Int? = null,
        /**
         * Şık kutuları ne zamandan beri çizilmiş durumda (0 = henüz değil).
         *
         * Soru kartı ekrana solarak geliyor ve bu sırada şıklar yerlerine
         * kayıyor. Metin daha kart kararmışken okunabildiği için, dokunuş
         * eski konumlara gidiyor ve **başka bir şıkka** basılıyordu: günlükte
         * "OTOMATİK → C" yazıp ekranda A'nın turkuaza döndüğü kareler bundan.
         * Üstelik oyunun o yanlış şıkka verdiği tepki doğru cevap diye
         * arşive yazılıyordu. Bu yüzden bekleme süresi sorunun okunduğu andan
         * değil, kutuların çizildiği andan itibaren sayılıyor.
         */
        var brightSince: Long = 0L
    )
    @Volatile private var pendingAnswer: PendingAnswer? = null
    @Volatile private var burstJob: Job? = null
    private val workerRunning = AtomicBoolean(false)
    @Volatile private var totalSaved = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        repo = Repo.get(this)
        auto = AutoPlayer(this) { line -> log(line) }
        running.value = true

        applyTargets(prefs.state.value.targetPackages)
        if (prefs.state.value.autoPlay) ensurePolling()

        scope.launch {
            var wasAuto = prefs.state.value.autoPlay
            prefs.state.collect { s ->
                applyTargets(s.targetPackages)
                if (s.autoPlay != wasAuto) {
                    wasAuto = s.autoPlay
                    auto.reset()
                    autoJob?.cancel()
                    if (s.autoPlay) ensurePolling()
                    log(if (s.autoPlay) "otomatik mod açıldı" else "manuel moda dönüldü")
                }
            }
        }
        scope.launch {
            repo.totalCount.collect { totalSaved = it }
        }
        Log.i(TAG, "Servis bağlandı")
    }

    /** Sadece seçilen uygulamaları dinle — pil ve gizlilik açısından önemli. */
    private fun applyTargets(targets: Set<String>) {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            // Oyun ekranı kendi yüzeyine çizdiği için genelde gelmez, ama
            // geldiğinde karara bakmak için bedava bir tetikleyici oluyor.
            AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 200
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        // Hedef seçilmediyse hiçbir olayı dinleme.
        info.packageNames = if (targets.isEmpty()) arrayOf(NO_PACKAGE) else targets.toTypedArray()
        runCatching { serviceInfo = info }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!::prefs.isInitialized) return

        val s = prefs.state.value
        if (s.paused || s.targetPackages.isEmpty()) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in s.targetPackages) return

        // Oyun ekranı kendi yüzeyine çizildiğinde Android hiç "içerik değişti"
        // olayı üretmez; bu yüzden hedef uygulama önplandayken olayları
        // beklemeden düzenli aralıklarla da tarıyoruz.
        ensurePolling()

        // Bir şıkka dokunduğun görülebildiyse karar birazdan açılacak demektir.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val waiting = pendingAnswer
            if (waiting != null && s.detectAnswer) {
                val (w, h) = ProjectionService.screenSize(this)
                startVerdictBurst(waiting, w, h)
                return
            }
        }

        requestScan()
    }

    /**
     * Hedef uygulama önplanda olduğu sürece belirli aralıklarla tarama ister.
     * Uygulamadan çıkılınca kendiliğinden durur: her turda etkin pencerenin
     * hangi uygulamaya ait olduğuna bakar.
     */
    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var misses = 0
            try {
                while (isActive) {
                    delay(if (fastCapture) POLL_FAST_MS else POLL_SLOW_MS)
                    val cur = prefs.state.value
                    if (cur.targetPackages.isEmpty()) break

                    val active = withContext(Dispatchers.Main) {
                        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                    }
                    if (active != null && active in cur.targetPackages) {
                        misses = 0
                        if (!cur.paused) requestScan()
                        // Dokunulmayı bekleyen bir soru varken hızlı yoklama.
                        // Eskiden koşul "kart henüz ölçülmedi" idi; ölçüm artık
                        // sorunun kaydedildiği anda yapıldığı için o koşul hep
                        // yanlış çıkıyor ve yoklama yavaş moda dönüyordu —
                        // dokunuş vakti gelse bile 800 ms'ye kadar bekleniyordu.
                        if (pendingAnswer?.taps == 0) delay(POLL_FAST_MS)
                    } else {
                        misses++
                        // Otomatik modda araya giren bir reklam ya da sistem
                        // penceresi yüzünden yoklamayı bırakırsak bot orada
                        // takılı kalır; bu yüzden çok daha uzun bekleriz.
                        val limit = if (cur.autoPlay) POLL_MISS_LIMIT_AUTO else POLL_MISS_LIMIT
                        if (misses >= limit) break
                    }
                }
            } finally {
                pollJob = null
            }
        }
    }

    /**
     * Ekran değiştikçe onlarca olay yağar. Her olayda yeni bir iş başlatıp
     * öncekini iptal etmek, olaylar sık geldiğinde hiç tarama yapılamamasına
     * yol açar. Bunun yerine tek bir işçi çalışır; yeni olaylar sadece
     * "kirli" bayrağını kaldırır.
     */
    private fun requestScan() {
        dirty = true
        if (!workerRunning.compareAndSet(false, true)) return

        scope.launch {
            try {
                var waited = 0L
                while (dirty) {
                    dirty = false
                    // Ekranın oturmasını bekle: animasyon bitmeden okumak yarım metin verir.
                    delay(if (fastCapture) SETTLE_FAST_MS else SETTLE_MS)
                    waited += if (fastCapture) SETTLE_FAST_MS else SETTLE_MS
                    // Ekranda sürekli dönen bir sayaç varsa olaylar hiç kesilmez;
                    // bu durumda sonsuza kadar beklemeyip yine de bir tarama yaparız.
                    if (dirty && waited < MAX_SETTLE_WAIT_MS) continue
                    waited = 0L
                    val cur = prefs.state.value
                    if (cur.paused || cur.targetPackages.isEmpty()) break
                    runCatching { scan(cur) }
                        .onFailure { Log.w(TAG, "Tarama hatası: ${it.message}") }
                    delay(MIN_SCAN_GAP_MS)
                }
            } finally {
                workerRunning.set(false)
                // İşçi kapanırken sızan bir olay olduysa yeniden başlat.
                if (dirty) requestScan()
            }
        }
    }

    // -----------------------------------------------------------------------

    private suspend fun scan(s: Prefs.Settings) {
        // Karar açılmak üzereyken hızlı renk turu çalışıyor; ekran görüntüsü
        // hakkını onunla paylaşmayalım.
        if (burstJob?.isActive == true) return

        val (screenW, screenH) = ProjectionService.screenSize(this)

        // Tarama arka planda kuyruğa girdiği için, sıra geldiğinde sen başka
        // bir uygulamaya geçmiş olabilirsin. Bu kontrol olmadan uygulama
        // ekranda ne varsa onu okuyordu — kendi Teşhis ekranını ve sistem
        // pencerelerini soru sanıp kaydetmesinin sebebi buydu.
        val front = withContext(Dispatchers.Main) {
            runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        }
        if (front == null || front !in s.targetPackages) {
            if (front != lastLoggedFront) {
                lastLoggedFront = front
                log("atlandı · önplanda: ${front ?: "bilinmiyor"}")
            }
            return
        }
        lastLoggedFront = front

        // Otomatik mod: ekranda cevabı beklenen bir soru varsa şıklardan
        // birine dokun. Tarama turunun başında duruyor ki soru ekranda
        // kaldığı sürece her turda yeniden denenebilsin.
        if (s.autoPlay) autoAnswerTick(s, screenW, screenH)

        val nodes = withContext(Dispatchers.Main) {
            runCatching { NodeHarvester.harvest(rootInActiveWindow) }.getOrDefault(emptyList())
        }

        var parsed = QuestionParser.parse(nodes, screenW, screenH, s, fromAccessibility = true)
        var source = CaptureSource.ACCESSIBILITY

        // Yerel val kullanıyoruz: Kotlin'in akıllı dönüşümü var üzerinde çalışmaz.
        val viaNodes = parsed
        val needOcr = s.ocrFallback &&
            (s.ocrAlways || viaNodes == null || viaNodes.confidence < 0.55f)

        // Bir süredir soru görmüyorsak tur bitmiş olabilir. Tur sonu ekranındaki
        // düğmeyi bulabilmek için ekranı okumamız şart: oyun metni çoğu zaman
        // erişilebilirlik ağacında görünmediğinden, OCR yedeği kapalı olsa bile
        // bu durumda ekran görüntüsü alıp metni tanıyoruz.
        val autoIdle = s.autoPlay && s.autoRestart &&
            SystemClock.uptimeMillis() - lastQuestionAt > AUTO_IDLE_MS

        // Cevabı beklenen bir soru varsa, OCR gerekmese bile ekran görüntüsü
        // alıyoruz — yoksa cevabın açıldığı anı hiç göremeyiz.
        val waiting = pendingAnswer
        var shot: Bitmap? =
            if (needOcr || autoIdle || (s.detectAnswer && waiting != null)) captureScreen()
            else null

        // --- 1. Ekran anlamlı biçimde değişti mi? -----------------------------
        //
        // Aynı kareyi yeniden okumamak işlemciyi koruyor; ama bekleyen soru
        // yokken bu atlama bizi kör ediyordu. Soru ekranda dururken ilk
        // okuma "3/4 şık" diye reddedilince sonraki kareler "aynı ekran"
        // sayılıp sessizce atlanıyor, ne yeni bir deneme yapılıyor ne de
        // günlüğe satır düşüyordu: 83 saniyelik boşluklar bundan. Şimdi
        // bekleyen soru yoksa kıpırdamayan ekran da belirli aralıkla baştan
        // okunuyor — ikinci okuma aynı sonucu verse bile en azından günlükte
        // sebebi görünüyor ve yakın plan OCR şansını buluyor.
        var frameStatic = false
        if (shot != null) {
            val sig = frameSignature(shot)
            val prev = lastFrameSig
            val now = SystemClock.uptimeMillis()
            if (sig != null && prev != null && sameFrame(sig, prev)) {
                frameStatic = true
                // Bekleyen soru varken kartın oturduğunu HENÜZ ölçmediysek
                // kareyi atlamak yasak. Oturmuş bir kart zaten kıpırdamayan
                // karedir: tam ölçmemiz gereken an burasıdır. Atlayınca
                // brightSince hiç kurulmuyor, otomatik dokunuş da her seferinde
                // 4 saniyelik emniyet süresini bekliyordu — ayarlardaki
                // "dokunmadan önce bekleme" değeri bu yüzden hiçbir işe
                // yaramıyordu.
                val kartOlculmeli = waiting != null && waiting.brightSince == 0L
                val force = kartOlculmeli ||
                    (waiting == null && now - lastForcedScanAt >= STATIC_RESCAN_MS)
                if (!autoIdle && !force) {
                    shot.recycle()
                    return
                }
                if (force && waiting == null) lastForcedScanAt = now
            } else {
                lastFrameChangeAt = now
                closeUpSig = null
            }
            if (sig != null) lastFrameSig = sig
        }

        // --- 2. Gerekiyorsa OCR ----------------------------------------------
        // Hızlı yolda kareler saniyede birkaç kez gelebiliyor; OCR bunların
        // hepsinde çalışırsa işlemciyi boğar. Metin tanımayı ayrıca kısıyoruz —
        // renk kontrolü ise her karede yapılmaya devam ediyor.
        // Tur sonu ekranı kıpırdamadığı için orada metin tanımayı daha da
        // seyrekleştiriyoruz; düğmeyi bir saniye geç bulmanın zararı yok.
        val ocrGap = if (autoIdle && !needOcr) AUTO_IDLE_OCR_GAP_MS else MIN_OCR_GAP_MS
        val ocrDue = SystemClock.uptimeMillis() - lastOcrAt >= ocrGap
        var ocrItems: List<TextItem> = emptyList()
        if ((needOcr || autoIdle) && shot != null && ocrDue) {
            lastOcrAt = SystemClock.uptimeMillis()
            ocrItems = OcrEngine.recognize(shot)
            lastOcrItems = ocrItems
            var viaOcr = QuestionParser.parse(
                ocrItems, shot.width, shot.height, s, fromAccessibility = false
            )
            // Dört şıktan üçü okundu ve ekran bir süredir kıpırdamıyor: bu
            // artık "şıklar teker teker beliriyor" hâli değil, OCR'ın bir
            // şıkkı görmemesi. Tek karakterlik şıklarda ("1") ML Kit bloğu
            // sık düşürüyor. Şık şeridini kırpıp iki kat büyüterek bir kez
            // daha okuyoruz; küçük harfler büyütülünce tanınıyor.
            if (viaOcr == null && shot != null && frameStatic &&
                QuestionParser.lastReject?.startsWith("şıklar henüz tamamlanmadı") == true &&
                SystemClock.uptimeMillis() - lastFrameChangeAt >= CLOSEUP_AFTER_MS &&
                lastFrameSig?.let { sig -> closeUpSig?.let { sameFrame(it, sig) } } != true
            ) {
                closeUpSig = lastFrameSig
                val onceki = ocrItems.size
                val yakin = closeUpOptions(shot, s)
                if (yakin != null) {
                    ocrItems = yakin
                    viaOcr = QuestionParser.parse(
                        ocrItems, shot.width, shot.height, s, fromAccessibility = false
                    )
                    log(
                        "yakın plan OCR: $onceki → ${ocrItems.size} metin · " +
                            (viaOcr?.let { "${it.options.size} şık bulundu" }
                                ?: "RED: ${QuestionParser.lastReject ?: "?"}")
                    )
                }
            }
            if (viaOcr != null && (viaNodes == null || viaOcr.confidence > viaNodes.confidence + 0.04f)) {
                parsed = rescale(viaOcr, shot.width, shot.height, screenW, screenH)
                source = if (nodes.isNotEmpty()) CaptureSource.HYBRID else CaptureSource.OCR
            }
        }
        // OCR kısıldığı turlarda teşhis dökümünü boş verilerle ezmeyelim.
        if (nodes.isNotEmpty() || ocrItems.isNotEmpty()) dumpDebug(nodes, ocrItems, parsed)

        // --- 3. Karar açıldı mı? ----------------------------------------------
        if (shot != null && waiting != null && s.detectAnswer) {
            val hint = if (nodes.isNotEmpty() || ocrItems.isNotEmpty()) {
                timedOut(nodes, ocrItems)
            } else null
            val settled = evaluateAnswer(shot, waiting, screenW, screenH, hint)
            // Yeşil göründü ama henüz onaylanmadı: karar bir iki saniyede
            // açılıp geçtiği için hızlı renk turuna geçiyoruz.
            if (!settled && (waiting.greenIndex != null || waiting.pendingIndex != null)) {
                startVerdictBurst(waiting, screenW, screenH)
            }
        }

        // --- 4. Yeni soru mu? -------------------------------------------------
        val p = parsed
        if (p == null) {
            if (ocrItems.isNotEmpty() || nodes.size > 1) {
                log("düğüm:${nodes.size} ocr:${ocrItems.size} · RED: ${QuestionParser.lastReject ?: "?"}")
            }
            // Ekranda soru yok ve bir süredir de yoktu: tur bitmiş olabilir.
            if (autoIdle) tryContinue(nodes, ocrItems, shot, screenW, screenH)
            shot?.let { if (!it.isRecycled) it.recycle() }
            return
        }
        if (p.confidence < s.minConfidence) {
            log("${p.options.size} şık %${(p.confidence * 100).toInt()} · RED: güven eşiğin altında")
            shot?.let { if (!it.isRecycled) it.recycle() }
            return
        }
        // Ekranda gerçek bir soru var: tur sonu yoklamasının sayacı sıfırlanır.
        lastQuestionAt = SystemClock.uptimeMillis()
        lastIdleScreen = null
        if (s.autoPlay) auto.noteQuestion()
        // Aynı soru numarası duruyorsa bu hâlâ aynı sorudur: ekran değişmiş
        // olabilir ama yeni bir kayıt açılmaz. Numara okunamadıysa (null)
        // eski davranışa düşüyoruz. Soru metni tamamen başkalaşmışsa da
        // kilidi açıyoruz — numarayı yanlış okumuş olabiliriz ve kilitli
        // kalmak yakalamayı tümden durdurur.
        val no = p.number
        val locked = no != null && no == lockedNumber &&
            TurkishText.similarity(p.question, lockedQuestion) > LOCK_MIN_SIMILARITY

        if (locked || p.key == lastKey) {
            // Şıklar animasyonla yerine oturuyor. Oyun şıkların sırasını
            // değiştirdiğinde burada `options` ve `rects` BİRLİKTE
            // tazelenmek zorunda.
            //
            // Eskiden yalnızca kutular tazeleniyor, metinler ilk okumadan
            // kalıyordu. O zaman "2. metin" ile "2. kutu" başka şıklara ait
            // oluyor: bot bir şıkka basıp oyun başkasında tepki veriyor,
            // arşive yanlış cevap yazılıyor ve otomatik mod sonraki turlarda
            // o yanlış cevaba basmaya devam ediyordu — hata kendini besliyordu.
            pendingAnswer?.let { waiting ->
                if (waiting.id == currentEncounterId) {
                    if (sameOrder(waiting.options, p.options)) {
                        // Sıra aynı: kutular animasyonla biraz kaymış olabilir.
                        waiting.rects = p.optionRects
                    } else if (waiting.options.size == p.options.size) {
                        // Sıra değişti. Metinle kutu birlikte güncelleniyor.
                        waiting.options = p.options
                        waiting.rects = p.optionRects
                        // Konuma bağlı bütün durum artık geçersiz: hangi
                        // kutunun yeşil olduğu, hangisine basıldığı, kaç
                        // saniyedir beklenildiği — hepsi yeniden okunmalı.
                        waiting.greenIndex = null
                        waiting.greenSince = 0L
                        waiting.pendingIndex = null
                        waiting.pendingSince = 0L
                        waiting.chosenIndex = null
                        waiting.knownIndex = null
                        waiting.lastSummary = null
                        waiting.lastSkip = null
                        // Yeni bir karşılaşma gibi sıfırla: dokunma gecikmesi
                        // bu andan sayılsın, kart yeniden otursun diye beklesin.
                        waiting.bornAt = SystemClock.uptimeMillis()
                        waiting.brightSince = 0L
                        waiting.taps = 0
                        waiting.lastTapAt = 0L
                        waiting.autoTapped = false
                        // Uçuşta olan dokunuş ve kayıt işleri bu artıştan
                        // eski yerleşimle çalıştıklarını anlayıp vazgeçsin.
                        waiting.layout++
                    }
                    // Sayılar uyuşmuyorsa (bir tarafta OCR şık düşürmüşse)
                    // hiçbir şeye dokunmuyoruz: yanlış eşleştirme riski var.
                }
            }
            shot?.let { if (!it.isRecycled) it.recycle() }
            return
        }

        // Kart oturmadan kaydetmiyoruz. Soru kartı solarak geliyor ve bu
        // sırada metin yarı saydam, kayar hâlde; OCR harfleri yanlış okuyor
        // ("yıldıza" yerine "yildza") ve bozuk metin arşive ayrı bir kayıt
        // olarak düşüyor. Yarım saniye beklemek, sonradan elle temizlenmesi
        // gereken çift kayıttan ucuz.
        if (shot != null &&
            !AnswerColorDetector.optionsRendered(shot, p.optionRects, screenW, screenH)
        ) {
            confirmKey = null
            shot.let { if (!it.isRecycled) it.recycle() }
            return
        }

        // Aynı okumayı iki kez üst üste görmeden yeni kayıt açmıyoruz.
        //
        // Sorular birbirine solarak geçiyor: kart henüz çizilmemişken OCR
        // yarım kalmış metni okuyor ve her karede başka türlü bozuyor.
        // Bu karelerden biri dört "şık" bulabiliyor ve arşive harfleri
        // karışmış bir soru düşüyordu — hemen ardından gerçek soru ayrıca
        // kaydediliyor, yani her geçişte bir çöp kayıt. Gerçek soru saniyede
        // birkaç kez okunduğu için ikinci okumayı beklemenin maliyeti yok;
        // bozuk okuma ise kendini iki kez aynı biçimde tekrar edemiyor.
        // Bu kapı bozuk geçiş okumalarına karşı. Soru arşivde bu haliyle
        // zaten varsa (parmak izi soru metni + sıralanmış şıklardan
        // hesaplanıyor) bozuk okuma olamaz: bozulmuş bir metin kendini daha
        // önce kaydedilmiş bir kayıtla birebir aynı üretemez. O yüzden
        // tanıdık sorularda ikinci okumayı beklemiyoruz; her soruda bir
        // tarama turu (300-600 ms) kazanıyoruz.
        val taniniyor = runCatching { repo.isKnownFingerprint(p.key) }.getOrDefault(false)
        if (!taniniyor && p.key != confirmKey) {
            confirmKey = p.key
            shot?.let { if (!it.isRecycled) it.recycle() }
            return
        }
        lastKey = p.key

        // Kullanıcı Ana ekrandan bir kategori seçtiyse o kazanır; yoksa ekrandan tanınan kullanılır.
        val category = s.activeCategory.takeIf { it.isNotBlank() } ?: p.category

        var shotPath: String? = null
        if (s.saveScreenshots) {
            if (shot == null) shot = captureScreen()
            shotPath = shot?.let { saveShot(it, p.key) }
        }

        val result = repo.save(
            question = p.question,
            options = p.options,
            category = category,
            source = source,
            confidence = p.confidence,
            screenshotPath = shotPath
        )
        val savedId = when (result) {
            is Repo.SaveResult.Inserted -> result.id
            is Repo.SaveResult.Duplicate -> result.id
            Repo.SaveResult.Rejected -> null
        }

        if (savedId == null) {
            log("RED: kayıt çok kısa / şık yetersiz")
            pendingAnswer = null
        } else {
            // Aynı soru ekranı saniyede birkaç kez taranıyor ve her tarama
            // küçük OCR farkları yüzünden ayrı bir kayıt denemesi oluyor.
            // Bunların hepsi TEK bir karşılaşmadır — sayaç yalnızca gerçekten
            // başka bir soruya geçildiğinde artar.
            val newEncounter = savedId != currentEncounterId
            if (newEncounter) {
                currentEncounterId = savedId
                // Yeni kayıt zaten seenCount = 1 ile başlıyor.
                if (result is Repo.SaveResult.Duplicate) repo.countEncounter(savedId)
            }

            val conf = (p.confidence * 100).toInt()
            // Numara günlüğe de yazılıyor: kilit mekanizmasının doğru sayıyı
            // okuyup okumadığı ancak cihazda görülebiliyor.
            val noLabel = no?.let { " · soru $it" } ?: " · numarasız"
            when {
                result is Repo.SaveResult.Inserted -> {
                    Log.i(TAG, "Kaydedildi #$savedId")
                    log("${p.options.size} şık %$conf · KAYDEDİLDİ #$savedId$noLabel")
                }
                newEncounter -> log("${p.options.size} şık %$conf · tekrar #$savedId$noLabel")
                // Aynı ekranın yeniden okunması: günlüğe yazmaya değmez.
            }
            // Sorunun kendisi ve ekrandaki şık sırası, karşılaşma başına bir
            // kez. Bunlar olmadan sonraki "OTOMATİK → C" ve "ÇELİŞKİ"
            // satırları havada kalıyordu: hangi metne dokunulduğu, OCR'ın
            // şıkları doğru okuyup okumadığı görülemiyordu.
            if (newEncounter) {
                log("SORU #$savedId «${p.question.take(70)}»")
                log(
                    "ŞIKLAR #$savedId: " +
                        p.options.mapIndexed { i, o -> "${'A' + i}«${o.take(24)}»" }
                            .joinToString(" ")
                )
            }

            // Soru dört şıkkıyla düzgün okundu: numara artana kadar bunu
            // yeniden okumaya çalışmayalım.
            if (no != null && p.options.size >= 4) {
                lockedNumber = no
                lockedQuestion = p.question
            }

            val answeredJustNow = savedId == lastAnsweredId &&
                SystemClock.uptimeMillis() - lastAnsweredAt < ANSWER_COOLDOWN_MS
            val current = pendingAnswer
            if (!answeredJustNow && (current == null || current.id != savedId)) {
                // Kartın oturduğu bu noktada ZATEN ölçülmüş durumda: birkaç
                // satır yukarıdaki optionsRendered() kapısını geçemeseydik
                // buraya hiç gelmezdik. Eskiden brightSince 0 ile başlıyor ve
                // ölçüm için bir tarama turu daha bekleniyordu; dokunuş o tur
                // kadar (300-600 ms) geç gidiyordu. Ölçümü burada kabul etmek
                // hiçbir güvenliği gevşetmiyor, sadece aynı bilgiyi iki kez
                // toplamayı bırakıyor.
                val now = SystemClock.uptimeMillis()
                pendingAnswer = PendingAnswer(savedId, p.optionRects, p.options).apply {
                    if (shot != null) brightSince = now
                }
            }
        }

        shot?.let { if (!it.isRecycled) it.recycle() }
    }

    // --- Otomatik mod --------------------------------------------------------

    /**
     * Ekrandaki soruya otomatik olarak dokunur.
     *
     * Dokunmadan önce şıkların yerine oturması için kısa bir süre bekleriz
     * ([Prefs.Settings.autoAnswerDelayMs]); aynı bekleme sorunun dört şıkla
     * birlikte kaydedilmesine de zaman tanıyor. Dokunuş ayrı bir işte yapılır,
     * yoksa jestin tamamlanmasını beklerken tarama döngüsü durur.
     */
    private fun autoAnswerTick(s: Prefs.Settings, screenW: Int, screenH: Int) {
        val waiting = pendingAnswer
        if (waiting == null) {
            // Soru yakalanamadıysa bot dokunmaz — ama bunu sessizce
            // yapmasın. Sebep, ayrıştırıcının son red gerekçesi: teşhis
            // ekranında "neden bekliyor" sorusu ancak böyle cevaplanıyor.
            val since = SystemClock.uptimeMillis() - lastQuestionAt
            if (since > AUTO_IDLE_LOG_AFTER_MS) {
                noteAutoIdle("bekleyen soru yok · son red: ${QuestionParser.lastReject ?: "?"}")
            }
            return
        }
        if (waiting.rects.size < 2) {
            noteAutoIdle("şık kutusu ${waiting.rects.size}, dokunulmaz")
            return
        }
        if (waiting.taps >= AUTO_MAX_TAPS) return
        if (autoJob?.isActive == true) return

        // İlk dokunuş şıklar yerine otursun diye bekliyor. Sonrakiler yeniden
        // deneme: jest sisteme başarıyla gönderilse bile oyunun onu yuttuğu
        // oluyor ve soru ekranda süre dolana kadar öylece kalıyordu. Cevap
        // açılınca soru kapandığı için fazladan dokunuş atılmıyor.
        val now = SystemClock.uptimeMillis()
        // Bekleme, sorunun okunduğu andan değil şık kutularının çizildiği
        // andan başlıyor. Renk okuması kapalıysa ya da kart bir türlü
        // oturmadıysa sorunun geldiği ana geri düşüyoruz, yoksa hiç
        // dokunmamış oluruz.
        val cardAt = when {
            !s.detectAnswer -> waiting.bornAt
            waiting.brightSince > 0L -> maxOf(waiting.bornAt, waiting.brightSince)
            now - waiting.bornAt > CARD_READY_TIMEOUT_MS -> waiting.bornAt
            else -> return
        }
        val due = if (waiting.taps == 0) cardAt + s.autoAnswerDelayMs
                  else waiting.lastTapAt + AUTO_RETAP_MS
        if (now < due) return

        autoJob = scope.launch {
            val cur = prefs.state.value
            if (!cur.autoPlay || cur.paused) return@launch
            // Bekleme sırasında soru değişmiş olabilir.
            if (pendingAnswer?.bornAt != waiting.bornAt) return@launch

            // Kutu sayısı ile metin sayısı tutmuyorsa hangi kutunun hangi
            // metne ait olduğunu bilmiyoruz. Eskiden burada sessizce
            // rastgeleye düşülüyordu — üstelik günlük yine "bilinen cevap"
            // yazdığı için teşhis imkânsızdı. Dokunmamak yeğ: soru zaten
            // bir sonraki karede yeniden okunacak.
            val layoutAtStart = waiting.layout
            if (waiting.rects.size != waiting.options.size) {
                log(
                    "otomatik atlandı #${waiting.id}: şık kutusu " +
                        "(${waiting.rects.size}) ve metin (${waiting.options.size}) " +
                        "sayısı tutmuyor"
                )
                return@launch
            }

            // Soruyu daha önce görmüşsek doğru şıkka, görmemişsek rastgele
            // birine dokunuyoruz. Kayıttaki sıra değil, kayıttaki doğru
            // cevabın o anki ekrandaki sırası aranıyor: şıklar karışıyor.
            val lookup = if (cur.autoUseKnownAnswer) {
                runCatching { repo.knownAnswerOnScreen(waiting.id, waiting.options) }
                    .getOrDefault(Repo.KnownAnswer.None)
            } else Repo.KnownAnswer.None

            // Arşivde cevap olduğu hâlde ekranda bulunamadıysa bu bir arıza:
            // sessizce rastgeleye düşmek yerine sebebini yazıyoruz.
            (lookup as? Repo.KnownAnswer.Unmatched)?.let {
                log(
                    "UYUŞMAZLIK #${waiting.id}: arşivdeki cevap " +
                        (it.text?.let { t -> "«$t»" } ?: "(kayıt bozuk)") +
                        " ekranda bulunamadı → rastgele seçiliyor · ekranda: " +
                        waiting.options.mapIndexed { i, o -> optionLabel(waiting, i) }
                            .joinToString(" ")
                )
            }

            val known = (lookup as? Repo.KnownAnswer.OnScreen)?.index

            val retry = waiting.taps > 0
            val index = waiting.chosenIndex
                ?: auto.pickOption(waiting.rects.size, known).also { waiting.chosenIndex = it }
            // Arşiv bir şık gösterdiği hâlde ona basmıyorsak sebebi
            // görünsün; yoksa "bilinen cevap" yazıp başka yere basmış
            // oluyorduk.
            if (known != null && index != known) {
                log(
                    "SAPMA #${waiting.id}: arşiv ${optionLabel(waiting, known)} diyor ama " +
                        "${optionLabel(waiting, index)} seçildi " +
                        "(kutu sayısı ${waiting.rects.size})"
                )
            }

            // Sayaçlar dokunuştan önce artıyor: jest başarısız olsa bile bu
            // bir denemedir, yoksa saniyede birkaç kez yeniden denenirdi.
            waiting.taps++
            waiting.lastTapAt = SystemClock.uptimeMillis()
            waiting.autoTapped = true
            // Yeni dokunuşun tepkisi ayrıca ölçülsün.
            waiting.tintSeen = false
            waiting.knownIndex = known

            // Arşiv sorgusu sürerken şıklar karışmış olabilir; o zaman
            // elimizdeki kutu artık başka bir metne ait.
            if (waiting.layout != layoutAtStart) {
                log("otomatik iptal #${waiting.id}: şıklar dokunmadan önce karıştı")
                return@launch
            }
            if (!auto.tapOption(waiting.rects, index, longPress = retry)) {
                log("otomatik: #${waiting.id} şıkkına dokunulamadı")
                return@launch
            }
            // Neden rastgele seçtiğimizi de yazıyoruz: "yeni soru" ile
            // "arşivde cevap var ama bulunamadı" bambaşka iki durum.
            val neden = when (lookup) {
                is Repo.KnownAnswer.OnScreen -> "bilinen cevap"
                is Repo.KnownAnswer.Unmatched -> "rastgele (eşleşmedi)"
                Repo.KnownAnswer.None ->
                    if (cur.autoUseKnownAnswer) "rastgele (cevabı bilinmiyor)" else "rastgele"
            }
            val gecikme = SystemClock.uptimeMillis() - waiting.bornAt
            log(
                "OTOMATİK #${waiting.id} → ${optionLabel(waiting, index)} · $neden" +
                    (if (retry) " · ${waiting.taps}. deneme" else "") +
                    " · ${gecikme} ms" +
                    (if (waiting.brightSince == 0L) " (kart ölçülemedi)" else "") +
                    " · bu oturumda ${auto.tapCount} cevap"
            )
            // Dokunduk; karar bir iki saniyede açılıp geçecek. Renk turunu
            // hemen başlatıyoruz ki cevabı kaçırmayalım.
            if (cur.detectAnswer) startVerdictBurst(waiting, screenW, screenH)
        }
    }

    /**
     * Tur sonu ekranında "Tekrar Oyna" benzeri düğmeyi arar ve basar.
     *
     * OCR kutuları ekran görüntüsünün ölçeğinde geldiği için önce ekran
     * koordinatlarına çeviriyoruz; erişilebilirlik düğümleri zaten ekran
     * koordinatlarında.
     */
    private suspend fun tryContinue(
        nodes: List<TextItem>,
        ocr: List<TextItem>,
        shot: Bitmap?,
        screenW: Int,
        screenH: Int
    ) {
        if (nodes.isEmpty() && ocr.isEmpty()) return
        val shotW = shot?.takeIf { !it.isRecycled }?.width ?: screenW
        val shotH = shot?.takeIf { !it.isRecycled }?.height ?: screenH
        val items = nodes + ocr.map {
            it.copy(bounds = scaleRect(it.bounds, shotW, shotH, screenW, screenH))
        }
        // "Atla"/"Devam" gibi yazılara ancak ekran iyice uzun süredir
        // kımıldamıyorsa dokunuruz; "Tekrar Oyna" için o kadar beklemeye gerek yok.
        val allowDismiss = SystemClock.uptimeMillis() - lastQuestionAt > AUTO_DISMISS_IDLE_MS
        when (val sonuc = auto.pressContinue(items, screenH, allowDismiss)) {
            // Az önce basıldı, sonucu bekleniyor. Ekranı raporlamaya gerek yok.
            AutoPlayer.Continue.Waiting -> return
            // Basılacak bir şey yok: ekranda ne yazdığını bir kez günlüğe
            // düşürüyoruz. Tur sonu ekranı oyundan oyuna değişiyor; hangi
            // düğmenin tanınmadığını ancak böyle görebiliyoruz.
            AutoPlayer.Continue.NotFound -> {
                logIdleScreen(items)
                return
            }
            is AutoPlayer.Continue.Pressed -> {
                log("OTOMATİK: \"${sonuc.label}\" → yeni tur (${auto.restartCount}. kez)")
                // Yeni tur birinci sorudan başlıyor; eski numara kilidi kalkmalı.
                lockedNumber = null
            }
        }
        // Ekran değişecek; bir sonraki kare yeniden okunsun.
        lastFrameSig = null
        lastKey = null
        pendingAnswer = null
    }

    /**
     * Tur sonu ekranında tanıdık bir düğme bulunamadığında ekrandaki yazıları
     * bir kez günlüğe yazar. Aynı ekran için tekrar tekrar yazmaz.
     */
    private fun logIdleScreen(items: List<TextItem>) {
        val labels = items
            .filter { it.text.trim().length in 2..28 }
            .sortedBy { it.centerY }
            .map { it.text.trim().replace('\n', ' ') }
            .distinct()
            .take(12)
        if (labels.isEmpty()) return
        val line = labels.joinToString(" | ")
        if (line == lastIdleScreen) return
        lastIdleScreen = line
        log("tur sonu · tanınan düğme yok · ekranda: $line")
    }

    /**
     * Bir şıkkı günlükte "C «Ayı»" biçiminde yazar.
     *
     * Sadece harf yazmak teşhisi imkânsız kılıyordu: şıklar her turda
     * karıştığı için "arşiv C diyordu, doğrusu A" satırı, arşivin yanlış mı
     * olduğunu yoksa dokunuşun mu kaydığını söylemiyordu. Metinle birlikte
     * ikisi bir bakışta ayrılıyor.
     */
    private fun optionLabel(waiting: PendingAnswer, index: Int): String {
        val harf = if (index in 0..3) ('A' + index).toString() else "?$index"
        val metin = waiting.options.getOrNull(index) ?: return "$harf «?»"
        return "$harf «${metin.take(28)}»"
    }

    /**
     * Bir renk okuması karar sayılmadı — sebebini bir kez yazar.
     *
     * Atlanan kareler eskiden hiç görünmüyordu; günlükte "renk: YESIL" yazıp
     * arkasından hiçbir şey olmaması "acaba kaçırdı mı" sorusunu cevapsız
     * bırakıyordu.
     */
    private fun skipVerdict(
        waiting: PendingAnswer,
        neden: String,
        a: AnswerColorDetector.Analysis
    ) {
        val satir = "karar atlandı #${waiting.id}: $neden · ${a.detail()}"
        if (satir == waiting.lastSkip) return
        waiting.lastSkip = satir
        log(satir)
    }

    /** Aynı sebep beş saniyede bir; günlüğü boğmadan "ne bekliyor" görünsün. */
    private fun noteAutoIdle(neden: String) {
        val now = SystemClock.uptimeMillis()
        if (neden == lastAutoIdleReason && now - lastAutoIdleLogAt < AUTO_IDLE_LOG_GAP_MS) return
        lastAutoIdleReason = neden
        lastAutoIdleLogAt = now
        log("otomatik: dokunmuyorum · $neden")
    }

    /** İki şık listesi aynı metinleri aynı sırada mı taşıyor? */
    private fun sameOrder(a: List<String>, b: List<String>): Boolean =
        a.size == b.size && a.indices.all {
            TurkishText.normalizeKey(a[it]) == TurkishText.normalizeKey(b[it])
        }

    private fun scaleRect(r: Rect, fromW: Int, fromH: Int, toW: Int, toH: Int): Rect {
        if (fromW == toW && fromH == toH) return Rect(r)
        val sx = toW.toFloat() / fromW.coerceAtLeast(1)
        val sy = toH.toFloat() / fromH.coerceAtLeast(1)
        return Rect(
            (r.left * sx).toInt(), (r.top * sy).toInt(),
            (r.right * sx).toInt(), (r.bottom * sy).toInt()
        )
    }

    /**
     * Karar durumunu tek bir kare üzerinden değerlendirir.
     * Kaydedildiyse true döner.
     *
     * İki ayrı durumu birbirinden ayırmak zorundayız:
     *   • Şıkka yeni dokundun — senin şıkkın yeşil bantta ama doğru mu
     *     yanlış mı belli değil.
     *   • Karar açıldı — yeşil olan gerçekten doğru cevap.
     *
     * Kırmızı görünüyorsa karar kesin açılmıştır, hemen kaydederiz.
     * Kırmızı yoksa aynı şıkkı iki ardışık örnekte görmeyi şart koşarız;
     * seçimin yanlış olsaydı ikinci örnekte kırmızıya dönmüş olurdu.
     */
    private suspend fun evaluateAnswer(
        shot: Bitmap,
        waiting: PendingAnswer,
        screenW: Int,
        screenH: Int,
        timedOutHint: Boolean?
    ): Boolean {
        val layout = waiting.layout
        val a = AnswerColorDetector.analyze(shot, waiting.rects, screenW, screenH)

        // Şık kutuları çizildi mi? Ölçüm doğrudan parlaklığa bakıyor.
        //
        // İki incelik var. Birincisi: eskiden "renkli bir şık varsa kart
        // hazırdır" diyorduk, ama korunmak istediğimiz şey zaten geçiş
        // karesinin yanlışlıkla renkli okunmasıydı — kontrol kendi kendini
        // iptal ediyordu. İkincisi: bir kez oturan kart geri "çizilmemiş"
        // hâle dönmez. Karar açılınca kırmızıya dönen şık koyulaşıyor
        // (184,152,168 gibi) ve eski kural o karede bayrağı sıfırlayıp
        // beklemeyi baştan başlatıyordu.
        if (waiting.brightSince == 0L && a.cardRendered(CARD_READY_MIN)) {
            waiting.brightSince = SystemClock.uptimeMillis()
        }

        // Renk deseni her değiştiğinde günlüğe düşüyor; kaçan cevapların
        // sebebini tahmin etmek yerine akışı görebilmek için.
        val summary = a.summary()
        if (a.tints.any { it != AnswerColorDetector.Tint.NEUTRAL }) waiting.tintSeen = true
        if (summary != waiting.lastSummary && summary.contains(Regex("YESIL|turkuaz|KIRMIZI"))) {
            waiting.lastSummary = summary
            log("renk #${waiting.id}: $summary")
        }

        val attempt = !(timedOutHint ?: false)

        // --- 1. Kararın yeşili göründü mü? -----------------------------------
        val correct = a.correctIndex
        if (correct != null) {
            // Kart daha ekrana oturmadıysa bu bir karar değil, beliriş
            // animasyonunun ortasından geçen bir karedir. Oyun kararı ancak
            // birisi (sen ya da bot) dokunduktan sonra açıyor; soru belirir
            // belirmez gelen "yeşil" fizikselen mümkün değil.
            if (waiting.brightSince == 0L) {
                skipVerdict(waiting, "kart henüz çizilmedi", a)
                waiting.greenIndex = null
                return false
            }
            val now = SystemClock.uptimeMillis()
            if (waiting.greenIndex != correct) {
                waiting.greenIndex = correct
                waiting.greenSince = now
                return false
            }
            // Kırmızı varsa karar kesin açılmıştır, ama yine de tek kareye
            // güvenmiyoruz: geçiş karelerinde bir şık yeşil bandına, bir
            // başkası kırmızı bandına aynı anda düşebiliyor ve o tek kare
            // "renk (kesin)" damgasıyla arşive yazılıyordu. En güçlü kanıt
            // olduğu için sonraki doğru okumalar onu bir daha düzeltemiyor,
            // bot da her turda aynı yanlış şıkka basmaya devam ediyordu.
            val bekle = if (a.verdictCertain) CERTAIN_CONFIRM_MS else GREEN_CONFIRM_MS
            if (now - waiting.greenSince < bekle) return false

            val kesin = a.verdictCertain
            record(
                waiting, correct,
                if (kesin) Repo.AnswerEvidence.CERTAIN else Repo.AnswerEvidence.GREEN,
                userWasRight = !kesin, countAsAttempt = attempt, layout = layout
            )
            log(
                "CEVAP #${waiting.id} → ${optionLabel(waiting, correct)} · " +
                    (if (kesin) "bilemedin" else "bildin") + " · ${a.detail()}"
            )
            finishAnswer(waiting.id)
            return true
        }
        waiting.greenIndex = null

        // --- 2. Süre dolmuş, ekran kararmış mı? ------------------------------
        // Süre gerçekten dolduysa soru ekranda çoktandır duruyordur. Soru
        // belirdikten hemen sonra gelen ölçüm şıkların beliriş animasyonudur;
        // eskiden bunu süre dolmuş sanıp arşive yanlış cevap yazıyorduk.
        val dimmed = a.dimmedReveal?.takeIf {
            SystemClock.uptimeMillis() - waiting.bornAt >= MIN_TIMEOUT_MS
        }
        if (dimmed != null) {
            record(waiting, dimmed, Repo.AnswerEvidence.TIMEOUT, false,
                countAsAttempt = false, layout = layout)
            log(
                "CEVAP #${waiting.id} → ${optionLabel(waiting, dimmed)} · " +
                    "süre doldu (denemeye sayılmadı) · ${a.colorSummary()}"
            )
            finishAnswer(waiting.id)
            return true
        }

        // --- 3. Sadece turkuaz: dokundun, karar bekleniyor -------------------
        // Normalde turkuaz yarım saniyede yeşile ya da kırmızıya döner. Uzun
        // süre öyle kalıyorsa ölçümlerimizin dışında bir durum var demektir;
        // geri düşüş olarak yine doğru cevap kabul ediyoruz.
        val pending = a.pendingIndex
        if (pending == null) {
            // Hiçbir şey bulunamadı. Ekran karartılmışsa ölçülen renkleri
            // günlüğe yazıyoruz ki neden ayırt edilemediği görülebilsin.
            if (a.colors.isNotEmpty()) {
                val cs = a.colorSummary()
                if (cs != waiting.lastSummary) {
                    waiting.lastSummary = cs
                    log("karartma #${waiting.id}: $cs")
                }
            }
            waiting.pendingIndex = null
            return false
        }
        // Dokunuşu bot yaptıysa bu geri düşüş geçersiz: rastgele seçilen bir
        // şık, karar açılmadığı için doğru olmaz. Eskiden burası arşive
        // rastgele cevabı doğru diye yazıyordu ve otomatik mod sonraki
        // turlarda o yanlışa basmaya devam ediyordu — hata kendini besliyordu.
        // Cevapsız kalmak, yanlış cevaptan iyidir.
        if (waiting.autoTapped) return false

        val now = SystemClock.uptimeMillis()
        if (waiting.pendingIndex != pending) {
            waiting.pendingIndex = pending
            waiting.pendingSince = now
            return false
        }
        if (now - waiting.pendingSince < VERDICT_CONFIRM_MS) return false
        if (waiting.brightSince == 0L) {
            skipVerdict(waiting, "kart henüz çizilmedi", a)
            return false
        }
        record(waiting, pending, Repo.AnswerEvidence.TOUCH, true, attempt, layout)
        log("CEVAP #${waiting.id} → ${optionLabel(waiting, pending)} · bildin (turkuaz sabit kaldı)")
        finishAnswer(waiting.id)
        return true
    }

    /**
     * Doğru cevabı kaydeder ve arşivle çelişiyorsa bunu günlüğe düşürür.
     *
     * Çelişki satırı kıymetli: otomatik mod arşivdeki cevaba bastığında
     * oyun onu yanlış sayıyorsa, arşivdeki kayıt bozuktur. Bu satır olmadan
     * hata sessizce sürüyordu.
     */
    private suspend fun record(
        waiting: PendingAnswer,
        index: Int,
        evidence: Repo.AnswerEvidence,
        userWasRight: Boolean,
        countAsAttempt: Boolean,
        /** Rengin okunduğu andaki şık yerleşimi. */
        layout: Int = waiting.layout
    ) {
        // Renk okunduktan sonra şıklar karıştıysa, gördüğümüz yeşil kutu
        // artık başka bir metne ait: o cevabı yazmak arşivi bozar.
        if (waiting.layout != layout) {
            log("atlandı #${waiting.id}: renk okunduktan sonra şıklar karıştı")
            return
        }
        // Son emniyet: kutu sayısı ile metin sayısı tutmuyorsa hangi rengin
        // hangi şıkka ait olduğunu bilmiyoruz demektir. Yanlış cevap yazmaktansa
        // hiç yazmamak yeğ.
        if (waiting.rects.size != waiting.options.size) {
            log("atlandı #${waiting.id}: şık kutusu (${waiting.rects.size}) ve " +
                "metin (${waiting.options.size}) sayısı tutmuyor")
            return
        }
        waiting.knownIndex?.let { known ->
            if (known != index) {
                log(
                    "ÇELİŞKİ #${waiting.id}: arşiv ${optionLabel(waiting, known)} diyordu, " +
                        "oyun ${optionLabel(waiting, index)} dedi"
                )
            }
        }
        repo.recordReveal(
            waiting.id, index, waiting.options,
            userWasRight = userWasRight,
            countAsAttempt = countAsAttempt,
            evidence = evidence
        )
    }

    private fun log(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        scanLog.value = (listOf("$stamp  $line") + scanLog.value).take(LOG_LIMIT)
    }

    private fun finishAnswer(id: Long) {
        // Aynı turda ikinci kez cevap yazılmasın: oyun sonu ekranı da
        // karartılmış olduğu için "süre doldu" kuralını tetikleyebiliyordu.
        lastAnsweredId = id
        lastAnsweredAt = SystemClock.uptimeMillis()
        pendingAnswer = null
        // Sonraki kare kararın rengini taşıyor; yeniden okunsun.
        lastFrameSig = null
        lastAutoIdleReason = null
        showCountNotification()
    }

    /**
     * Karar bir iki saniyede açılıp geçiyor. O aralıkta OCR ve ayrıştırmayla
     * vakit kaybetmemek için yalnızca ekran görüntüsü alıp renge bakan kısa
     * bir tur çalıştırıyoruz.
     */
    private fun startVerdictBurst(waiting: PendingAnswer, screenW: Int, screenH: Int) {
        if (burstJob?.isActive == true) return
        val fast = fastCapture
        // Dokunduktan sonra hiçbir renk kıpırdamıyorsa jest yutulmuş
        // demektir. Turu sonuna kadar (3 sn) sürdürmek yeniden denemeyi de
        // o kadar geciktiriyordu: tarama döngüsü tur bitene dek duruyor,
        // AUTO_RETAP_MS'in 2,5 saniyesi hiç işlemiyordu. Renk görülmeden
        // geçen kare sayısı sınırı dolunca turu erken bitiriyoruz.
        val tapCountAtStart = waiting.taps
        burstJob = scope.launch {
            var sessiz = 0
            repeat(if (fast) VERDICT_TRIES_FAST else VERDICT_TRIES_SLOW) {
                if (pendingAnswer?.id != waiting.id) return@launch
                if (tapCountAtStart > 0 && !waiting.tintSeen) {
                    if (++sessiz >= SILENT_BURST_LIMIT) {
                        log("otomatik #${waiting.id}: dokunuşa tepki yok, yeniden denenecek")
                        return@launch
                    }
                } else {
                    sessiz = 0
                }
                if (fast) {
                    delay(VERDICT_GAP_FAST_MS)
                    // peek() yeniden kullanılan kareyi döndürür: hiç bellek
                    // ayrılmaz, bu yüzden saniyede 20 kez bakmak ucuz.
                    // Dönen Bitmap recycle EDİLMEZ.
                    val bmp = ProjectionService.peek() ?: return@repeat
                    val done = runCatching {
                        evaluateAnswer(bmp, waiting, screenW, screenH, timedOutHint = null)
                    }.getOrDefault(false)
                    if (done) return@launch
                } else {
                    val bmp = captureScreen() ?: return@repeat
                    val done = runCatching {
                        evaluateAnswer(bmp, waiting, screenW, screenH, timedOutHint = null)
                    }.getOrDefault(false)
                    if (!bmp.isRecycled) bmp.recycle()
                    if (done) return@launch
                }
            }
        }
    }

    /**
     * Ekranda "Süre Bitti" yazıyorsa cevabı sen vermemişsindir; doğru cevap
     * yine kaydedilir ama başarı istatistiğine yanlışlıkla "bildin" diye geçmez.
     */
    private fun timedOut(vararg lists: List<TextItem>): Boolean =
        lists.any { list ->
            list.any {
                val k = TurkishText.normalizeKey(it.text)
                k.contains("surebitti") || k.contains("suredoldu")
            }
        }

    /**
     * Ekran karesi alır.
     *
     * İki yol var ve aralarındaki fark bu uygulamanın işe yarayıp
     * yaramamasını belirliyor:
     *
     *  • **Ekran yansıtma (hızlı):** sistem ekranı sürekli aynalar, kareyi
     *    istediğimiz an alırız. Hız sınırı yoktur. Şıkların teker teker
     *    belirmesini ve cevabın açıldığı yarım saniyeyi ancak böyle
     *    yakalayabiliyoruz. Ekran değişmediyse yeni kare üretilmediği için
     *    null döner — bu da bize bedava "değişiklik yok" bilgisi verir.
     *
     *  • **Erişilebilirlik ekran görüntüsü (yavaş):** Android bunu saniyede
     *    bir kereden fazla çağırmaya izin vermiyor. Yansıtma kapalıyken
     *    tek seçenek bu, ama hızlı olaylar kaçar.
     */
    private suspend fun captureScreen(): Bitmap? {
        if (ProjectionService.isRunning) return ProjectionService.grab()

        val now = SystemClock.uptimeMillis()
        val since = now - lastShotAt
        if (since < SHOT_MIN_GAP_MS) delay(SHOT_MIN_GAP_MS - since)
        lastShotAt = SystemClock.uptimeMillis()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeShotViaAccessibility()?.let { return it }
        }
        return null
    }

    /** Hızlı yol açık mı? Tarama ve karar yoklama aralıkları buna göre değişir. */
    private val fastCapture: Boolean get() = ProjectionService.isRunning

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeShotViaAccessibility(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            var out: Bitmap? = null
                            try {
                                val hw = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(hw, screenshot.colorSpace)
                                out = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                hw.close()
                            } catch (t: Throwable) {
                                Log.w(TAG, "Görüntü dönüştürülemedi: ${t.message}")
                            }
                            if (cont.isActive) cont.resume(out)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.d(TAG, "Ekran görüntüsü alınamadı (kod $errorCode)")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun saveShot(bmp: Bitmap, key: String): String? = runCatching {
        val dir = File(filesDir, "shots").apply { mkdirs() }
        val target = File(dir, "${key.take(24)}.jpg")
        if (target.exists()) return target.absolutePath

        val maxW = 820
        val scaled = if (bmp.width > maxW) {
            val ratio = maxW.toFloat() / bmp.width
            Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * ratio).toInt(), true)
        } else bmp

        FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, 72, it) }
        if (scaled !== bmp) scaled.recycle()
        target.absolutePath
    }.getOrNull()

    /** Ekranın kaba bir gri tonlamalı özeti — kare karşılaştırması için. */
    /**
     * Şık şeridini kırpıp iki kat büyüterek yeniden okur; sonucu ekranın
     * geri kalanındaki metinlerle birleştirip döndürür.
     *
     * Şerit dışındaki metinler (soru, sayaç) ilk okumadan olduğu gibi
     * kalıyor; yalnızca şık bölgesi yakın plandan geliyor. Kutular
     * büyütülmüş görüntüden gelip tam ekran ölçeğine geri çevriliyor —
     * yoksa dokunuş iki kat aşağıya giderdi.
     */
    private suspend fun closeUpOptions(shot: Bitmap, s: Prefs.Settings): List<TextItem>? {
        val top = (s.optionsTop * shot.height).toInt().coerceIn(0, shot.height - 2)
        val bottom = (s.optionsBottom * shot.height).toInt().coerceIn(top + 2, shot.height)
        val crop = runCatching {
            Bitmap.createBitmap(shot, 0, top, shot.width, bottom - top)
        }.getOrNull() ?: return null
        val big = runCatching {
            Bitmap.createScaledBitmap(crop, crop.width * CLOSEUP_SCALE, crop.height * CLOSEUP_SCALE, true)
        }.getOrNull()
        if (big == null) { crop.recycle(); return null }
        val items = runCatching { OcrEngine.recognize(big) }.getOrDefault(emptyList())
        if (big !== crop) big.recycle()
        crop.recycle()
        if (items.isEmpty()) return null

        val mapped = items.map { it ->
            it.copy(
                bounds = Rect(
                    it.bounds.left / CLOSEUP_SCALE,
                    it.bounds.top / CLOSEUP_SCALE + top,
                    it.bounds.right / CLOSEUP_SCALE,
                    it.bounds.bottom / CLOSEUP_SCALE + top
                )
            )
        }
        val disaridakiler = lastOcrItems.filter { it.centerY !in top..bottom }
        return disaridakiler + mapped
    }

    private fun frameSignature(bmp: Bitmap): IntArray? = runCatching {
        val small = Bitmap.createScaledBitmap(bmp, SIG_W, SIG_H, true)
        val px = IntArray(SIG_W * SIG_H)
        small.getPixels(px, 0, SIG_W, 0, 0, SIG_W, SIG_H)
        if (small !== bmp) small.recycle()
        IntArray(px.size) { i ->
            val c = px[i]
            ((c shr 16 and 0xFF) * 30 + (c shr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
        }
    }.getOrNull()

    /** Küçük farklar (geri sayan sayaç gibi) aynı ekran sayılır. */
    private fun sameFrame(a: IntArray, b: IntArray): Boolean {
        if (a.size != b.size) return false
        var sum = 0L
        for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
        return sum / a.size < FRAME_DIFF_LIMIT
    }

    private fun rescale(
        p: QuestionParser.Parsed,
        fromW: Int, fromH: Int, toW: Int, toH: Int
    ): QuestionParser.Parsed {
        if (fromW == toW && fromH == toH) return p
        val sx = toW.toFloat() / fromW.coerceAtLeast(1)
        val sy = toH.toFloat() / fromH.coerceAtLeast(1)
        return p.copy(
            optionRects = p.optionRects.map {
                Rect((it.left * sx).toInt(), (it.top * sy).toInt(),
                     (it.right * sx).toInt(), (it.bottom * sy).toInt())
            }
        )
    }

    /** Teşhis ekranında "ekranda ne gördü" sorusunu yanıtlamak için. */
    private fun dumpDebug(
        nodes: List<TextItem>,
        ocr: List<TextItem>,
        parsed: QuestionParser.Parsed?
    ) {
        if (!::prefs.isInitialized) return
        val sb = StringBuilder()
        sb.append("Zaman: ").append(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())).append('\n')
        sb.append("Erişilebilirlik metinleri: ").append(nodes.size).append('\n')
        nodes.take(30).forEach {
            sb.append("  • [").append(if (it.clickable) "tık" else "   ").append("] ")
                .append(it.bounds.top).append("-").append(it.bounds.bottom).append("  ")
                .append(it.text.take(70)).append('\n')
        }
        if (ocr.isNotEmpty()) {
            sb.append("OCR blokları: ").append(ocr.size).append('\n')
            ocr.take(30).forEach {
                sb.append("  • ").append(it.bounds.top).append("-").append(it.bounds.bottom)
                    .append("  ").append(it.text.replace('\n', ' ').take(70)).append('\n')
            }
        }
        sb.append("\nSonuç: ")
        if (parsed == null) {
            sb.append("ayrıştırılamadı — ")
                .append(QuestionParser.lastReject ?: "sebep bilinmiyor")
        } else {
            sb.append("güven %").append((parsed.confidence * 100).toInt()).append('\n')
            sb.append("SORU: ").append(parsed.question).append('\n')
            parsed.options.forEachIndexed { i, o ->
                sb.append("  ").append(('A' + i)).append(") ").append(o).append('\n')
            }
            parsed.category?.let { sb.append("Kategori: ").append(it).append('\n') }
        }
        prefs.setDebugDump(sb.toString())
    }

    private fun showCountNotification() {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, ArsivApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_capture)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$totalSaved soru arşivlendi")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(intent)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running.value = false
        pollJob?.cancel()
        burstJob?.cancel()
        autoJob?.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running.value = false
        pollJob?.cancel()
        burstJob?.cancel()
        autoJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SoruArsivi/Capture"
        private const val NOTIF_ID = 7
        private const val NO_PACKAGE = "com.emre.bilbakalim.arsiv.hicbiri"

        /** Animasyon bitsin diye beklenen süre (yavaş yol). */
        private const val SETTLE_MS = 260L
        /** Hızlı yolda kare farkı zaten koruduğu için kısa bekleme yeter. */
        private const val SETTLE_FAST_MS = 90L
        /** Ekran hiç durulmasa bile en geç bu kadar sonra yine de tara. */
        private const val MAX_SETTLE_WAIT_MS = 1700L
        /** İki tarama arasında bırakılan nefes payı. */
        private const val MIN_SCAN_GAP_MS = 300L
        /** takeScreenshot API'sinin hız sınırı. */
        private const val SHOT_MIN_GAP_MS = 1150L
        /** Olay gelmese bile hedef uygulama önplandayken tarama aralığı. */
        private const val POLL_FAST_MS = 200L
        private const val POLL_SLOW_MS = 800L
        /** İki metin tanıma arasında bırakılan en az süre. */
        private const val MIN_OCR_GAP_MS = 450L
        /** Tur sonu ekranı kıpırdamıyor; orada metin tanıma aralığı. */
        private const val AUTO_IDLE_OCR_GAP_MS = 1200L
        /** Bu kadar turda hedef uygulama önplanda değilse yoklamayı bırak. */
        private const val POLL_MISS_LIMIT = 3
        /**
         * Otomatik modda aynı sınır: reklam ya da sistem penceresi araya
         * girdiğinde bot orada takılı kalmasın diye çok daha uzun.
         */
        private const val POLL_MISS_LIMIT_AUTO = 40
        /**
         * Otomatik modda bu kadar süredir soru görülmüyorsa tur bitmiş
         * sayılır ve "Tekrar Oyna" düğmesi aranmaya başlanır. Cevap açılıp
         * sonraki sorunun gelmesi ~1,5 saniye sürdüğü için bunun üstünde.
         */
        private const val AUTO_IDLE_MS = 6000L
        /**
         * "Atla", "Devam", "Kapat" gibi yazılar soru ekranında da bulunabiliyor.
         * Onlara ancak bu kadar süredir hiç soru görülmediyse dokunuruz.
         */
        private const val AUTO_DISMISS_IDLE_MS = 12_000L
        /**
         * Karar yoklama. Hızlı yolda ~120 ms'de bir, toplam ~3 saniye:
         * senin seçimin dokunuştan ~0,1 sn, gerçek cevap ~0,5 sn sonra
         * belirdiği için bu pencere ikisini de rahatça yakalıyor.
         */
        private const val VERDICT_TRIES_FAST = 60
        private const val VERDICT_GAP_FAST_MS = 50L
        /**
         * Kırmızı yoksa kararı onaylamadan önce beklenen süre. Senin
         * seçimin dokunuştan ~0,1 sn sonra beliriyor, gerçek karar ~0,5 sn
         * sonra açılıyor; 900 ms ikisinin arasını güvenle aşıyor.
         */
        private const val VERDICT_CONFIRM_MS = 900L
        /**
         * Karar yeşili göründü ama kırmızı yok. Kırmızı senin şıkkında birkaç
         * kare geç belirebileceği için bu kadar bekleyip öyle "bildin" diyoruz.
         * Doğru cevapta oyun seni bekletmeden geçtiği için kısa tutuldu.
         */
        private const val GREEN_CONFIRM_MS = 300L
        /**
         * Kırmızı da görüldüğünde beklenen doğrulama süresi.
         *
         * Kırmızı kararın açıldığını gösterdiği için yeşilden kısa; ama sıfır
         * değil. Tek kareye güvendiğimizde, beliriş/kapanış animasyonunda bir
         * şıkkın yeşil bandına başkasının kırmızı bandına aynı anda düştüğü
         * kareler "renk (kesin)" damgasıyla arşive yazılıyordu — en güçlü
         * kanıt olduğu için de bir daha düzelmiyordu. 50 ms'lik karelerde
         * bu üç kare demek.
         */
        private const val CERTAIN_CONFIRM_MS = 120L
        /** Bu süre içinde aynı soruya ikinci kez cevap yazılmaz. */
        private const val ANSWER_COOLDOWN_MS = 20_000L
        /** Otomatik modda bir soruya en fazla kaç kez dokunulur. */
        private const val AUTO_MAX_TAPS = 3
        /** Dokunuşa yanıt gelmezse bu kadar sonra yeniden denenir. */
        private const val AUTO_RETAP_MS = 2500L
        /**
         * Dokunuştan sonra kaç kare renk değişimi görmezsek jestin yutulduğuna
         * hükmedip renk turunu erken bitiriyoruz. 50 ms'lik karelerde ~1 sn.
         */
        private const val SILENT_BURST_LIMIT = 20
        /**
         * "Süre doldu" kararı için sorunun ekranda durması gereken en az süre.
         * Oyunun sayacı bir dakikanın üstünde olduğu için bu eşik gerçek bir
         * süre dolmasını hiçbir zaman kaçırmaz.
         */
        private const val MIN_TIMEOUT_MS = 20_000L
        /**
         * Şık kutusunun "çizildi" sayılması için en düşük parlaklık.
         * Oturmuş şıklar bembeyaz (0.97); geçiş kareleri koyu mor.
         */
        private const val CARD_READY_MIN = 217
        /**
         * Kart bu kadar sürede oturmadıysa yine de dokun.
         *
         * Bu bir emniyet süresi, normal yol değil. Uzun süre "normal yol"
         * sanıldı: kıpırdamayan kareler taramadan önce atlandığı için kartın
         * oturduğu hiç ölçülemiyor, her cevap tam 4 saniye sürüyordu.
         */
        private const val CARD_READY_TIMEOUT_MS = 4000L
        /**
         * Numara kilidinin geçerli sayılması için soru metninin eski metne
         * en az bu kadar benzemesi gerekir. Cevap animasyonu metni biraz
         * bozabiliyor; bambaşka bir metin ise numarayı yanlış okuduğumuz
         * anlamına gelir ve kilit açılır.
         */
        private const val LOCK_MIN_SIMILARITY = 0.5f
        private const val VERDICT_TRIES_SLOW = 5
        /** Kare imzası çözünürlüğü. */
        private const val SIG_W = 24
        private const val SIG_H = 48
        /**
         * Bu değerin altındaki ortalama fark "ekran değişmedi" demektir.
         * Turkuaza dönen bir şık ekranın ~%6'sını kaplar ve ortalama farkı
         * ancak ~3 birim değiştirir; eşik 4'te kalsaydı bu değişim kaçardı.
         */
        private const val FRAME_DIFF_LIMIT = 2
        /**
         * Bekleyen soru yokken kıpırdamayan ekranın yeniden okunma aralığı.
         * Aynı ekranı tekrar okumak genelde aynı sonucu verir; ama ilk
         * okumada bir şık kaçtıysa yakın plan denemesi ancak böyle sıraya
         * giriyor, ve günlük en azından "hâlâ neden bekliyoruz"u gösteriyor.
         */
        private const val STATIC_RESCAN_MS = 1500L
        /**
         * Yakın plan OCR'dan önce ekranın en az bu kadar kıpırdamamış olması
         * gerekiyor: şıklar teker teker belirirken 3/4 normaldir, o anda
         * büyüterek okumak boşuna işlemci harcar.
         */
        private const val CLOSEUP_AFTER_MS = 700L
        private const val CLOSEUP_SCALE = 2
        /** Soru görmeyeli bu kadar olduysa bot neden beklediğini yazsın. */
        private const val AUTO_IDLE_LOG_AFTER_MS = 3000L
        private const val AUTO_IDLE_LOG_GAP_MS = 5000L

        /** Arayüzün servisin açık olup olmadığını görmesi için. */
        val running = MutableStateFlow(false)

        /**
         * Son taramaların tek satırlık özeti — Teşhis ekranı bunu gösterir.
         * Tek bir kareyi gösteren döküm, "hangi soru neden kaçtı" sorusunu
         * yanıtlamaya yetmiyordu; bu liste zaman içindeki akışı veriyor.
         */
        /**
         * Bellekte tutulan satır sayısı.
         *
         * Arayüzde bunun yalnızca ilk [LOG_VISIBLE] satırı gösteriliyor; asıl
         * yığın burada duruyor. Bir sorunu fark ettiğinde onu doğuran satırlar
         * çoktan ekrandan kaymış oluyor — "Paylaş" düğmesi geçmişin tamamını
         * dışarı veriyor.
         */
        const val LOG_LIMIT = 4000
        /** Teşhis ekranında gösterilen satır sayısı. */
        const val LOG_VISIBLE = 80
        val scanLog = MutableStateFlow<List<String>>(emptyList())
    }
}
