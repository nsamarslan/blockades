package com.emre.bilbakalim.arsiv.capture

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.app.NotificationCompat
import com.emre.bilbakalim.arsiv.ArsivApp
import com.emre.bilbakalim.arsiv.R

/**
 * Ekran yakalama yedeği.
 *
 * Android 11 ve üstünde erişilebilirlik servisi `takeScreenshot()` ile
 * izin penceresi olmadan görüntü alabiliyor; bu servis sadece Android 10 ve
 * altında ya da o yol başarısız olduğunda devreye girer.
 */
class ProjectionService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var w = 0
    private var h = 0

    private val handler = Handler(Looper.getMainLooper())

    /** En son ne zaman GERÇEKTEN yeni bir kare alındı. */
    @Volatile private var lastImageAt = 0L
    /** Üst üste kaç kez kare alınamadı (hata fırlatarak). */
    @Volatile private var acquireFails = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "Ekran yakalama durduruldu")
            teardown()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Servis zaten kuruluyken ikinci kez başlatılırsa (izin penceresinden
        // dönüş, sistemin yeniden teslimi) eskisini bırakmadan yenisini kurmak
        // ekranı kimsenin okumadığı bir okuyucuya aynalayan ölü bir sanal ekran
        // bırakıyordu: ölü ekran çizilmeye devam ediyor, işlemciyi boş yere
        // yiyordu.
        if (projection != null || reader != null || virtualDisplay != null) teardown()

        try {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val mp = mgr.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
            mp.registerCallback(projectionCallback, handler)
            projection = mp

            val size = screenSize(this)
            w = size.first
            h = size.second
            val dpi = resources.displayMetrics.densityDpi

            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, IMAGE_BUFFERS)
            virtualDisplay = mp.createVirtualDisplay(
                "SoruArsiviEkran", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler
            )
            lastImageAt = SystemClock.uptimeMillis()
            acquireFails = 0
            instance = this
            running.value = true
            Log.i(TAG, "Ekran yakalama hazir: ${w}x$h")
        } catch (t: Throwable) {
            Log.e(TAG, "Ekran yakalama baslatilamadi: ${t.message}")
            teardown()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val n: Notification = NotificationCompat.Builder(this, ArsivApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Ekran okunuyor")
            .setSmallIcon(R.drawable.ic_notif_screen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // Kare başına yeni Bitmap ayırmak saniyede 20 karede ~200 MB/s çöp üretir.
    // Bu yüzden aynı iki tamponu tekrar tekrar kullanıyoruz.
    private var paddedFrame: Bitmap? = null
    private var exactFrame: Bitmap? = null
    private var hasFrame = false

    /**
     * ImageReader'daki en son kareyi okur.
     *
     * [copy] false ise **yeniden kullanılan** tampon döner: hızlı renk
     * kontrolü için idealdir, çağıran recycle ETMEMELİDİR ve bir sonraki
     * okumaya kadar kullanmalıdır. true ise bağımsız bir kopya döner;
     * OCR gibi asenkron işler bunu ister.
     *
     * Ekran değişmediyse sistem yeni kare üretmez; o durumda son kare döner.
     */
    @Synchronized
    private fun readFrame(copy: Boolean): Bitmap? {
        checkStall()
        val r = reader ?: return null
        val image = try {
            r.acquireLatestImage()
        } catch (t: Throwable) {
            // Bu hata eskiden sessizce yutuluyordu ve sonucu ağırdı: okuyucu
            // bozulduğunda (tampon tükenmesi, yansıtmanın sistemce kesilmesi)
            // aşağıdaki `hasFrame` dalı sonsuza kadar EN SON kareyi döndürmeye
            // devam ediyor. Uygulama donmuş bir görüntüyü saniyede onlarca kez
            // tarıyor, hiçbir renk değişmediği için karar turları hep sonuna
            // kadar işliyor, OCR aynı kareyi tekrar tekrar okuyor — dışarıdan
            // "birden yavaşladı" diye görünen tablo tam olarak bu. Kullanıcının
            // hızlı yakalamayı kapatıp açması da işe bu yüzden yarıyordu.
            if (acquireFails++ == 0) Log.w(TAG, "Kare alinamadi: ${t.message}")
            null
        }
        if (image != null) {
            acquireFails = 0
            lastImageAt = SystemClock.uptimeMillis()
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride.coerceAtLeast(1)
                val paddedW = (plane.rowStride / pixelStride).coerceAtLeast(w)

                var pad = paddedFrame
                if (pad == null || pad.width != paddedW || pad.height != h) {
                    pad?.recycle()
                    pad = Bitmap.createBitmap(paddedW, h, Bitmap.Config.ARGB_8888)
                    paddedFrame = pad
                }
                buffer.rewind()
                pad.copyPixelsFromBuffer(buffer)
                hasFrame = true
            } catch (t: Throwable) {
                Log.w(TAG, "Kare okunamadi: ${t.message}")
            } finally {
                runCatching { image.close() }
            }
        }

        if (!hasFrame) return null
        val pad = paddedFrame ?: return null

        // Satır hizalaması yüzünden tampon ekrandan geniş olabilir; kırpıyoruz.
        val frame = if (pad.width == w) pad else {
            var f = exactFrame
            if (f == null || f.width != w || f.height != h) {
                f?.recycle()
                f = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                exactFrame = f
            }
            Canvas(f).drawBitmap(pad, 0f, 0f, null)
            f
        }
        return if (copy) frame.copy(Bitmap.Config.ARGB_8888, false) else frame
    }

    /**
     * Akış ölmüş mü diye bakar; ölmüşse boru hattını kendi kendine yeniler.
     *
     * Kullanıcının elle yaptığı "kapat–aç" ile aynı şey, ama izin penceresi
     * olmadan: MediaProjection izni yerinde duruyor, yalnızca ona bağlı
     * okuyucu ve sanal ekran yeniden kuruluyor.
     */
    private fun checkStall() {
        if (projection == null) return
        val now = SystemClock.uptimeMillis()
        val neden = when {
            acquireFails >= ACQUIRE_FAIL_LIMIT -> "okuyucu $acquireFails kez hata verdi"
            lastImageAt != 0L && now - lastImageAt >= STALL_LIMIT_MS ->
                "${(now - lastImageAt) / 1000} sn yeni kare yok"
            else -> return
        }
        restartPipeline(neden)
    }

    private fun restartPipeline(neden: String) {
        val mp = projection ?: return
        Log.w(TAG, "Kare akisi yenileniyor: $neden")
        runCatching { virtualDisplay?.release() }
        runCatching { reader?.close() }
        virtualDisplay = null
        reader = null
        // Eski kare artık bir şey anlatmıyor: yenisi gelene kadar null dönelim
        // ki çağıran taraf donmuş görüntüyü "ekran değişmedi" sanmasın.
        hasFrame = false
        acquireFails = 0
        lastImageAt = SystemClock.uptimeMillis()
        val ok = runCatching {
            val dpi = resources.displayMetrics.densityDpi
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, IMAGE_BUFFERS)
            virtualDisplay = mp.createVirtualDisplay(
                "SoruArsiviEkran", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, handler
            )
            reader = r
        }.isSuccess
        if (ok) {
            restartCount++
        } else {
            Log.e(TAG, "Kare akisi yenilenemedi, hizli yakalama kapaniyor")
            teardown()
            stopSelf()
        }
    }

    private fun teardown() {
        runCatching { paddedFrame?.recycle() }
        runCatching { exactFrame?.recycle() }
        paddedFrame = null
        exactFrame = null
        hasFrame = false
        runCatching { virtualDisplay?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        virtualDisplay = null
        reader = null
        projection = null
        if (instance === this) {
            instance = null
            running.value = false
        }
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SoruArsivi/Projection"
        private const val NOTIF_ID = 42
        /**
         * Okuyucunun tampon sayısı.
         *
         * `acquireLatestImage()` en az 2 istiyor, ama 2'de üreticiye (sanal
         * ekranı çizen taraf) hiç boş tampon kalmıyor: biz okuyana kadar
         * yeni kare üretilemiyor. Üçüncü tampon, iki okuma arasında her
         * zaman taze bir kare hazır olmasını sağlıyor — karar penceresini
         * yakalamak tam da buna bağlı. Maliyeti bir ekran dolusu tampon.
         */
        private const val IMAGE_BUFFERS = 3
        /**
         * Bu kadar süredir yeni kare gelmiyorsa akış ölmüş sayılır.
         *
         * Oyun ekranında sayaç sürekli döndüğü için normalde iki kare
         * arası milisaniyelerle ölçülür; 15 saniyelik sessizlik gerçekten
         * duran bir ekran ya da kopmuş bir boru hattı demektir. İkisinde de
         * yeniden kurmanın zararı yok: duran ekranda yeni sanal ekran
         * hemen bir kare çiziyor.
         */
        private const val STALL_LIMIT_MS = 15_000L
        /** Üst üste bu kadar hatadan sonra okuyucu bozuk sayılır. */
        private const val ACQUIRE_FAIL_LIMIT = 5

        /**
         * Kare akışının kaç kez kendi kendine yenilendiği. Teşhis günlüğü
         * bunu izliyor: "yavaşladı" şikâyetinin sebebi buysa artık görünür.
         */
        @Volatile var restartCount = 0
            private set
        const val EXTRA_CODE = "sonuc_kodu"
        const val EXTRA_DATA = "sonuc_verisi"

        @Volatile private var instance: ProjectionService? = null

        /** Arayüzün hızlı yakalamanın açık olup olmadığını görmesi için. */
        val running = MutableStateFlow(false)

        val isRunning: Boolean get() = instance != null

        /** Bağımsız kopya — OCR ve kaydetme gibi asenkron işler için. */
        fun grab(): Bitmap? = instance?.readFrame(copy = true)

        /**
         * Yeniden kullanılan kare — yalnızca anlık renk kontrolü için.
         * Dönen Bitmap recycle EDİLMEMELİ; bir sonraki okumada üzerine yazılır.
         */
        fun peek(): Bitmap? = instance?.readFrame(copy = false)

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ProjectionService::class.java)) }
        }

        @Suppress("DEPRECATION")
        fun screenSize(context: Context): Pair<Int, Int> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(android.view.WindowManager::class.java)
                val b = wm.maximumWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val dm = android.util.DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(dm)
                dm.widthPixels to dm.heightPixels
            }
        }
    }
}
