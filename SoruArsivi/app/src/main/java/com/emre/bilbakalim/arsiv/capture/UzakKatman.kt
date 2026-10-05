package com.emre.bilbakalim.arsiv.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.emre.bilbakalim.arsiv.data.EkranBolgesi

/**
 * Uzak modun ekran üstü katmanı: yüzen düğme, doğru şıkkı gösteren ok ya
 * da "bilinmiyor" rozeti, ve bölge seçme penceresi.
 *
 * Pencereler erişilebilirlik katmanı türünde (TYPE_ACCESSIBILITY_OVERLAY):
 * erişilebilirlik servisi bunları ayrı bir "başka uygulamaların üstünde
 * göster" izni istemeden açabiliyor. Bütün çağrılar ana iş parçacığından.
 *
 * Konumlar ekran pikseli (sol üst köşeden); ekran görüntüsü de ekranın
 * tamamı olduğu için oranlar ikisinde aynı.
 */
class UzakKatman(private val ctx: Context, private val olay: Olaylar) {

    interface Olaylar {
        /** Düğmeye dokunuldu (sürüklenmedi). */
        fun dugmeyeBasildi()
        /** Düğme sürüklenip bırakıldı; yeni sol üst köşesi. */
        fun dugmeBirakildi(x: Int, y: Int)
    }

    /** Düğmenin görünümü. */
    enum class Ton(val zemin: Int) {
        MAVI(0xE01565C0.toInt()),
        GRI(0xD0424242.toInt()),
        YESIL(0xE02E7D32.toInt()),
        TURUNCU(0xE0EF6C00.toInt()),
        /** Yapay zekâ tahmini: arşivden bilinen cevabın yeşilinden ayrı dursun. */
        MOR(0xE06A1B9A.toInt()),
        KIRMIZI(0xE0C62828.toInt())
    }

    /** Okun ya da rozetin görünümü. */
    sealed interface Imge {
        /** [renk] null ise yeşil (arşivden bilinen cevap). */
        data class Ok(val sagaBakar: Boolean, val renk: Int? = null) : Imge
        data class Rozet(val metin: String, val renk: Int) : Imge
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val yogunluk = ctx.resources.displayMetrics.density

    private var dugme: TextView? = null
    private var dugmeLp: WindowManager.LayoutParams? = null
    private var isaret: ImgeView? = null
    private var isaretLp: WindowManager.LayoutParams? = null
    private var secim: View? = null
    private var gizli = false

    fun dp(v: Float): Int = (v * yogunluk + 0.5f).toInt()

    private fun lp(touchable: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    // --- Yüzen düğme -------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    fun dugmeKur(x: Int, y: Int) {
        if (dugme != null) return
        val tv = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            maxWidth = dp(260f)
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(Ton.MAVI.zemin)
            }
            text = "◎ Uzak mod"
        }
        val p = lp(touchable = true).apply { this.x = x; this.y = y }
        val esik = ViewConfiguration.get(ctx).scaledTouchSlop
        var bx = 0f; var by = 0f; var lx = 0; var ly = 0; var surukleniyor = false
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bx = e.rawX; by = e.rawY; lx = p.x; ly = p.y; surukleniyor = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - bx
                    val dy = e.rawY - by
                    if (!surukleniyor && dx * dx + dy * dy > esik * esik) surukleniyor = true
                    if (surukleniyor) {
                        p.x = (lx + dx).toInt()
                        p.y = (ly + dy).toInt()
                        runCatching { wm.updateViewLayout(tv, p) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (surukleniyor) olay.dugmeBirakildi(p.x, p.y) else olay.dugmeyeBasildi()
                }
            }
            true
        }
        if (runCatching { wm.addView(tv, p) }.isSuccess) {
            dugme = tv
            dugmeLp = p
            if (gizli) tv.visibility = View.GONE
        }
    }

    fun dugmeMetni(metin: String, ton: Ton) {
        val tv = dugme ?: return
        if (tv.text != metin) tv.text = metin
        (tv.background as? GradientDrawable)?.setColor(ton.zemin)
    }

    /** Düğmenin ekrandaki yeri ve boyu; henüz ölçülmediyse null. */
    fun dugmeKutusu(): UzakGeometri.Kutu? {
        val tv = dugme ?: return null
        val p = dugmeLp ?: return null
        if (tv.width <= 0 || tv.height <= 0) return null
        return UzakGeometri.Kutu(p.x, p.y, p.x + tv.width, p.y + tv.height)
    }

    fun dugmeTasi(x: Int, y: Int) {
        val tv = dugme ?: return
        val p = dugmeLp ?: return
        if (p.x == x && p.y == y) return
        p.x = x; p.y = y
        runCatching { wm.updateViewLayout(tv, p) }
    }

    /** Düğme yeniden ölçüldüğünde (metin değişti) çağrılacak iş. */
    fun dugmeOlculunce(is_: () -> Unit) {
        dugme?.post(is_)
    }

    // --- Ok / rozet ----------------------------------------------------------

    fun isaretGoster(yer: UzakGeometri.Isaret, imge: Imge) {
        var v = isaret
        if (v == null) {
            v = ImgeView(ctx)
            val p = lp(touchable = false).apply {
                width = yer.w; height = yer.h; x = yer.x; y = yer.y
            }
            if (runCatching { wm.addView(v, p) }.isFailure) return
            isaret = v
            isaretLp = p
        }
        val p = isaretLp ?: return
        v.imge = imge
        v.visibility = if (gizli) View.GONE else View.VISIBLE
        if (p.x != yer.x || p.y != yer.y || p.width != yer.w || p.height != yer.h) {
            p.x = yer.x; p.y = yer.y; p.width = yer.w; p.height = yer.h
            runCatching { wm.updateViewLayout(v, p) }
        }
        v.invalidate()
    }

    fun isaretGizle() {
        isaret?.visibility = View.GONE
        isaret?.imge = null
    }

    /** Kendi uygulamamız önplandayken ya da seçim sırasında her şey gizli. */
    fun gizle(v: Boolean) {
        gizli = v
        dugme?.visibility = if (v) View.GONE else View.VISIBLE
        isaret?.let { it.visibility = if (v || it.imge == null) View.GONE else View.VISIBLE }
    }

    fun kaldir() {
        secimKapat()
        dugme?.let { runCatching { wm.removeView(it) } }
        isaret?.let { runCatching { wm.removeView(it) } }
        dugme = null; dugmeLp = null; isaret = null; isaretLp = null
    }

    private inner class ImgeView(c: Context) : View(c) {
        var imge: Imge? = null
        private val dolgu = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val kenar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f).toFloat()
            color = Color.WHITE
            strokeJoin = Paint.Join.ROUND
        }
        private val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            when (val i = imge) {
                is Imge.Ok -> {
                    // Gövde + üçgen uç; uç şıkka bakıyor.
                    val govdeY = h * 0.30f
                    val ucX = w * 0.45f
                    val path = Path()
                    if (i.sagaBakar) {
                        path.moveTo(w * 0.05f, h / 2 - govdeY / 2)
                        path.lineTo(ucX, h / 2 - govdeY / 2)
                        path.lineTo(ucX, h * 0.12f)
                        path.lineTo(w * 0.95f, h / 2)
                        path.lineTo(ucX, h * 0.88f)
                        path.lineTo(ucX, h / 2 + govdeY / 2)
                        path.lineTo(w * 0.05f, h / 2 + govdeY / 2)
                    } else {
                        val sol = w - ucX
                        path.moveTo(w * 0.95f, h / 2 - govdeY / 2)
                        path.lineTo(sol, h / 2 - govdeY / 2)
                        path.lineTo(sol, h * 0.12f)
                        path.lineTo(w * 0.05f, h / 2)
                        path.lineTo(sol, h * 0.88f)
                        path.lineTo(sol, h / 2 + govdeY / 2)
                        path.lineTo(w * 0.95f, h / 2 + govdeY / 2)
                    }
                    path.close()
                    dolgu.color = i.renk ?: OK_RENGI
                    canvas.drawPath(path, dolgu)
                    canvas.drawPath(path, kenar)
                }
                is Imge.Rozet -> {
                    val r = minOf(w, h) / 2 - kenar.strokeWidth
                    dolgu.color = i.renk
                    canvas.drawCircle(w / 2, h / 2, r, dolgu)
                    canvas.drawCircle(w / 2, h / 2, r, kenar)
                    yazi.textSize = r * 1.2f
                    val y = h / 2 - (yazi.descent() + yazi.ascent()) / 2
                    canvas.drawText(i.metin, w / 2, y, yazi)
                }
                null -> Unit
            }
        }
    }

    // --- Bölge seçimi ------------------------------------------------------

    /**
     * Dondurulmuş kare üstünde soru ve şık bölgesini çizdirir.
     *
     * @param kutuBul şık bölgesi çizilince önizleme için hapları arar;
     *   sonucu (ekran oranı) verilen geri çağırmaya iletir. Arka planda
     *   çalıştırmak çağıranın işi.
     * @param bitti kaydedildiyse iki bölge, vazgeçildiyse null'lar.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun secimGoster(
        kare: Bitmap,
        ekranW: Int,
        ekranH: Int,
        mevcutSoru: EkranBolgesi?,
        mevcutSik: EkranBolgesi?,
        kutuBul: (EkranBolgesi, EkranBolgesi, (List<EkranBolgesi>) -> Unit) -> Unit,
        bitti: (EkranBolgesi?, EkranBolgesi?) -> Unit
    ) {
        secimKapat()
        val kok = FrameLayout(ctx)
        val cizim = CizimView(ctx, kare, ekranW, ekranH, mevcutSoru, mevcutSik)
        kok.addView(cizim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Panelin boş yerine dokunmak çizime geçmesin.
            isClickable = true
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0xE6202020.toInt())
            }
        }
        val bilgi = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val alt = TextView(ctx).apply {
            setTextColor(0xFFB0BEC5.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val satir = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }
        fun dugmeYap(yazi: String, renk: Int) = TextView(ctx).apply {
            text = yazi
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(renk)
            }
        }
        val iptal = dugmeYap("İptal", 0xFF616161.toInt())
        val tasi = dugmeYap("▲▼", 0xFF455A64.toInt())
        val geri = dugmeYap("Geri", 0xFF616161.toInt())
        val ileri = dugmeYap("İleri", 0xFF1565C0.toInt())
        for (d in listOf(iptal, tasi, geri, ileri)) {
            satir.addView(d, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6f)
            })
        }
        panel.addView(bilgi)
        panel.addView(alt)
        panel.addView(satir)
        val panelLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(dp(10f), dp(40f), dp(10f), dp(40f)) }
        kok.addView(panel, panelLp)

        var asama = 0
        var kutuSayisi: Int? = null
        fun yenile() {
            if (asama == 0) {
                bilgi.text = "1/2 · Soruyu çerçevele"
                alt.text = "Parmağınla soru kartının çevresine dikdörtgen çiz. " +
                    "Üstteki soru numarası da içinde kalabilir."
                ileri.text = "İleri"
                geri.visibility = View.INVISIBLE
                ileri.alpha = if (cizim.soru != null) 1f else 0.4f
            } else {
                bilgi.text = "2/2 · Dört şıkkı birlikte çerçevele"
                alt.text = when {
                    cizim.sik == null -> "Dört şıkkın hepsini içine alan tek bir dikdörtgen çiz."
                    kutuSayisi == null -> "Şık kutuları aranıyor…"
                    kutuSayisi!! >= 4 -> "✓ ${kutuSayisi} şık kutusu bulundu (mavi)."
                    else -> "Şık kutusu bulunamadı (${kutuSayisi}). Yine de kaydedebilirsin; " +
                        "şıklar metinden okunur. Yayında soru ekranı açıkken seçmek daha iyi."
                }
                ileri.text = "Kaydet"
                geri.visibility = View.VISIBLE
                ileri.alpha = if (cizim.sik != null) 1f else 0.4f
            }
            cizim.asama = asama
            cizim.invalidate()
        }
        cizim.cizildi = {
            if (asama == 1) {
                val s = cizim.soru
                val k = cizim.sik
                kutuSayisi = null
                cizim.kutular = emptyList()
                if (s != null && k != null) {
                    kutuBul(s, k) { bulunan ->
                        cizim.post {
                            if (cizim.sik == k) {
                                kutuSayisi = bulunan.size
                                cizim.kutular = bulunan
                                yenile()
                            }
                        }
                    }
                }
            }
            yenile()
        }
        iptal.setOnClickListener { secimKapat(); bitti(null, null) }
        tasi.setOnClickListener {
            panelLp.gravity = if (panelLp.gravity == Gravity.BOTTOM) Gravity.TOP else Gravity.BOTTOM
            panel.layoutParams = panelLp
        }
        geri.setOnClickListener { asama = 0; yenile() }
        ileri.setOnClickListener {
            if (asama == 0) {
                if (cizim.soru == null) return@setOnClickListener
                asama = 1
                cizim.cizildi?.invoke()
            } else {
                val s = cizim.soru ?: return@setOnClickListener
                val k = cizim.sik ?: return@setOnClickListener
                secimKapat()
                bitti(s, k)
            }
        }
        yenile()

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (runCatching { wm.addView(kok, p) }.isSuccess) secim = kok
        else bitti(null, null)
    }

    val secimAcik: Boolean get() = secim != null

    fun secimKapat() {
        secim?.let { runCatching { wm.removeView(it) } }
        secim = null
    }

    /**
     * Donmuş kare ve üstüne çizilen dikdörtgenler. Dokunuşlar ekran
     * koordinatıyla (rawX/rawY) alınıyor; pencere durum çubuğunun altından
     * başlasa da oranlar ekranın tamamına göre doğru çıkıyor.
     */
    @SuppressLint("ViewConstructor")
    private inner class CizimView(
        c: Context,
        private val kare: Bitmap,
        private val ekranW: Int,
        private val ekranH: Int,
        var soru: EkranBolgesi?,
        var sik: EkranBolgesi?
    ) : View(c) {
        var asama = 0
        var kutular: List<EkranBolgesi> = emptyList()
        var cizildi: (() -> Unit)? = null
        private var basX = 0f
        private var basY = 0f
        private val konum = IntArray(2)
        private val boya = Paint(Paint.FILTER_BITMAP_FLAG)
        private val cerceve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3f).toFloat()
        }
        private val golge = Paint().apply { color = 0x66000000 }
        private val etiket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = dp(14f).toFloat()
        }

        private fun ekranX(f: Float) = f * ekranW - konum[0]
        private fun ekranY(f: Float) = f * ekranH - konum[1]

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(konum)
            canvas.drawBitmap(
                kare, null,
                RectF(-konum[0].toFloat(), -konum[1].toFloat(),
                    (ekranW - konum[0]).toFloat(), (ekranH - konum[1]).toFloat()),
                boya
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), golge)
            soru?.let { ciz(canvas, it, SORU_RENGI, "Soru", kalin = asama == 0) }
            sik?.let { ciz(canvas, it, SIK_RENGI, "Şıklar", kalin = asama == 1) }
            cerceve.color = KUTU_RENGI
            cerceve.strokeWidth = dp(2f).toFloat()
            for (k in kutular) {
                canvas.drawRect(ekranX(k.sol), ekranY(k.ust), ekranX(k.sag), ekranY(k.alt), cerceve)
            }
        }

        private fun ciz(canvas: Canvas, b: EkranBolgesi, renk: Int, ad: String, kalin: Boolean) {
            val r = RectF(ekranX(b.sol), ekranY(b.ust), ekranX(b.sag), ekranY(b.alt))
            // Seçilen bölgenin içi karartmasız: ne seçildiği görünsün.
            canvas.save()
            canvas.clipRect(r)
            canvas.drawBitmap(
                kare, null,
                RectF(-konum[0].toFloat(), -konum[1].toFloat(),
                    (ekranW - konum[0]).toFloat(), (ekranH - konum[1]).toFloat()),
                boya
            )
            canvas.restore()
            cerceve.color = renk
            cerceve.strokeWidth = dp(if (kalin) 3f else 2f).toFloat()
            canvas.drawRect(r, cerceve)
            etiket.color = renk
            canvas.drawText(ad, r.left + dp(4f), (r.top - dp(6f)).coerceAtLeast(etiket.textSize), etiket)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val fx = (e.rawX / ekranW).coerceIn(0f, 1f)
            val fy = (e.rawY / ekranH).coerceIn(0f, 1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { basX = fx; basY = fy }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    val b = EkranBolgesi.ikiNoktadan(basX, basY, fx, fy)
                    val gecerli = b.gecerli
                    if (asama == 0) soru = if (gecerli) b else soru
                    else sik = if (gecerli) b else sik
                    invalidate()
                    if (e.actionMasked == MotionEvent.ACTION_UP && gecerli) cizildi?.invoke()
                }
            }
            return true
        }
    }

    companion object {
        private val OK_RENGI = 0xFF00C853.toInt()
        private val SORU_RENGI = 0xFFFFB300.toInt()
        private val SIK_RENGI = 0xFF43A047.toInt()
        private val KUTU_RENGI = 0xFF00B8D4.toInt()
    }
}
