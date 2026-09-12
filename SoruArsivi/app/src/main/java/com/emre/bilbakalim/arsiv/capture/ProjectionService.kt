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

        try {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val mp = mgr.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
            mp.registerCallback(projectionCallback, handler)
            projection = mp

            val size = screenSize(this)
            w = size.first
            h = size.second
            val dpi = resources.displayMetrics.densityDpi

            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mp.createVirtualDisplay(
                "SoruArsiviEkran", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, handler
            )
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
        val r = reader ?: return null
        val image = try { r.acquireLatestImage() } catch (_: Throwable) { null }
        if (image != null) {
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
