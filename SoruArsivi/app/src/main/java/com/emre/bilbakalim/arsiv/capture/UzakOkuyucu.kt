package com.emre.bilbakalim.arsiv.capture

import android.graphics.Bitmap
import android.graphics.Rect
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import com.emre.bilbakalim.arsiv.data.Prefs

/**
 * Uzak mod: yayının içindeki oyundan soruyu ve şıkları okur.
 *
 * Yerel yakalamanın parçalarını değiştirmeden kullanıyor: şık hapları
 * [OptionBoxFinder] ile ölçülüyor, metin [OcrEngine] ile okunuyor, soru ve
 * şıklar [QuestionParser] ile kuruluyor. Fark, bunların ekranın tamamına
 * değil, seçilen bölgelerden kırpılıp büyütülmüş parçaya uygulanması
 * (bkz. [UzakGeometri.cerceve]).
 */
object UzakOkuyucu {

    data class Okuma(
        val soru: String,
        val siklar: List<String>,
        /** Şık kutuları, ekran görüntüsüne oranla ([siklar] ile aynı sırada). */
        val sikKutulari: List<EkranBolgesi>,
        /** Şıklar pikselden ölçülen haplardan mı okundu (yoksa metin yolundan mı)? */
        val kutuYolu: Boolean
    )

    /** Okuma ya da neden okunamadığı. */
    data class Sonuc(val okuma: Okuma?, val red: String?)

    suspend fun oku(shot: Bitmap, soru: EkranBolgesi, sik: EkranBolgesi, s: Prefs.Settings): Sonuc {
        val parca = Parca.kes(shot, soru, sik) ?: return Sonuc(null, "seçilen bölge çok küçük")
        try {
            val bmp = parca.bmp
            val bw = bmp.width
            val bh = bmp.height
            val soruG = UzakGeometri.goreli(soru, parca.cerceve, shot.width, shot.height)
            val sikG = UzakGeometri.goreli(sik, parca.cerceve, shot.width, shot.height)
            // Ayrıştırıcının bölge ayarları kırpılmış parçaya göre.
            val ayar = s.copy(
                soruBolgesi = soruG,
                sikBolgesi = sikG,
                questionTop = (soruG.ust - PAY).coerceAtLeast(0f),
                questionBottom = (soruG.alt + PAY).coerceAtMost(1f),
                optionsTop = (sikG.ust - PAY).coerceAtLeast(0f),
                optionsBottom = (sikG.alt + PAY).coerceAtMost(1f),
                autoDetectCategory = false,
                requireFourOptions = true
            )

            val kutular = if (s.findOptionBoxes) kutulariBul(bmp, sikG) else emptyList()
            val ocr = OcrEngine.recognize(bmp)

            var p: QuestionParser.Parsed? = null
            var kutuYolu = false
            var red: String? = null
            if (kutular.size >= 3) {
                val metinler = kutuMetinleri(bmp, kutular, ocr)
                p = QuestionParser.parse(
                    ocr, bw, bh, ayar, fromAccessibility = false,
                    knownOptions = metinler, bolgeSecili = true
                )
                if (p != null) kutuYolu = true else red = QuestionParser.lastReject
            }
            if (p == null) {
                p = QuestionParser.parse(ocr, bw, bh, ayar, fromAccessibility = false, bolgeSecili = true)
            }
            if (p == null) {
                val neden = red ?: QuestionParser.lastReject ?: "okunamadı"
                val kutu = if (kutular.size >= 3) "kutu:${kutular.size}"
                    else "kutu:0 (${OptionBoxFinder.lastReason ?: "-"})"
                return Sonuc(null, "$neden · ocr:${ocr.size} · $kutu")
            }
            val oranli = p.optionRects.map { parca.ekranOrani(it, shot.width, shot.height) }
            return Sonuc(Okuma(p.question, p.options, oranli, kutuYolu), null)
        } finally {
            parca.birak(shot)
        }
    }

    /**
     * Bölge seçerken önizleme: seçilen şık bölgesinde kaç hap bulunuyor?
     * Kutular ekran görüntüsüne oranla.
     */
    fun kutular(shot: Bitmap, soru: EkranBolgesi, sik: EkranBolgesi): List<EkranBolgesi> {
        val parca = Parca.kes(shot, soru, sik) ?: return emptyList()
        try {
            val sikG = UzakGeometri.goreli(sik, parca.cerceve, shot.width, shot.height)
            return kutulariBul(parca.bmp, sikG).map {
                parca.ekranOrani(Rect(it.left, it.top, it.right, it.bottom), shot.width, shot.height)
            }
        } finally {
            parca.birak(shot)
        }
    }

    // ---------------------------------------------------------------------

    /** Kırpılmış (ve gerekiyorsa büyütülmüş) parça. */
    private class Parca(val bmp: Bitmap, val cerceve: UzakGeometri.Kutu, val olcek: Int, private val ara: Bitmap?) {

        /** Parçadaki dikdörtgenin ekran görüntüsüne oranı. */
        fun ekranOrani(r: Rect, w: Int, h: Int) = EkranBolgesi(
            (cerceve.sol + r.left / olcek).toFloat() / w,
            (cerceve.ust + r.top / olcek).toFloat() / h,
            (cerceve.sol + r.right / olcek).toFloat() / w,
            (cerceve.ust + r.bottom / olcek).toFloat() / h
        )

        fun birak(shot: Bitmap) {
            if (bmp !== shot && !bmp.isRecycled) bmp.recycle()
            if (ara != null && ara !== shot && ara !== bmp && !ara.isRecycled) ara.recycle()
        }

        companion object {
            fun kes(shot: Bitmap, soru: EkranBolgesi, sik: EkranBolgesi): Parca? {
                val c = UzakGeometri.cerceve(soru, sik, shot.width, shot.height)
                if (c.w < EN_AZ || c.h < EN_AZ) return null
                val kirpik = runCatching { Bitmap.createBitmap(shot, c.sol, c.ust, c.w, c.h) }
                    .getOrNull() ?: return null
                val k = UzakGeometri.olcek(c.w)
                if (k == 1) return Parca(kirpik, c, 1, null)
                val buyuk = runCatching {
                    Bitmap.createScaledBitmap(kirpik, c.w * k, c.h * k, true)
                }.getOrNull()
                if (buyuk == null) return Parca(kirpik, c, 1, null)
                return Parca(buyuk, c, k, kirpik)
            }
        }
    }

    private fun kutulariBul(bmp: Bitmap, sikG: EkranBolgesi): List<OptionBoxFinder.Box> {
        val bant = OptionBoxFinder.Bant(
            (sikG.sol * bmp.width).toInt(), (sikG.ust * bmp.height).toInt(),
            (sikG.sag * bmp.width).toInt(), (sikG.alt * bmp.height).toInt()
        )
        return OptionBoxFinder.find(bmp, bant).ifEmpty { OptionBoxFinder.find(bmp) }
    }

    /**
     * Her hapın metni: önce parçanın OCR'ından hapın içine düşenler,
     * bulunamazsa hap kırpılıp büyütülerek (yerel yakalamadaki
     * `optionTextsFromBoxes` ile aynı yol).
     */
    private suspend fun kutuMetinleri(
        bmp: Bitmap,
        kutular: List<OptionBoxFinder.Box>,
        ocr: List<TextItem>
    ): List<TextItem> = kutular.map { k ->
        val kutu = Rect(k.left, k.top, k.right, k.bottom)
        val pay = (kutu.height() * KUTU_PAYI).toInt()
        val icerdekiler = ocr.filter { t ->
            t.bounds.top >= kutu.top - pay && t.bounds.bottom <= kutu.bottom + pay &&
                t.centerX in kutu.left..kutu.right && t.centerY in kutu.top..kutu.bottom
        }
        var metin = QuestionParser.kutuMetni(
            icerdekiler.sortedBy { it.bounds.left }.map { it.text to it.bounds.left..it.bounds.right }
        )
        if (metin.isBlank()) metin = kutuyuOku(bmp, kutu)
        TextItem(metin, kutu)
    }

    private suspend fun kutuyuOku(bmp: Bitmap, kutu: Rect): String {
        val ic = (kutu.height() * KUTU_ICI).toInt()
        val l = (kutu.left + ic).coerceIn(0, bmp.width - 2)
        val t = (kutu.top + ic).coerceIn(0, bmp.height - 2)
        val r = (kutu.right - ic).coerceIn(l + 1, bmp.width)
        val b = (kutu.bottom - ic).coerceIn(t + 1, bmp.height)
        val kirp = runCatching { Bitmap.createBitmap(bmp, l, t, r - l, b - t) }.getOrNull() ?: return ""
        val buyuk = runCatching {
            Bitmap.createScaledBitmap(kirp, kirp.width * KUTU_OLCEK, kirp.height * KUTU_OLCEK, true)
        }.getOrNull()
        try {
            val items = runCatching { OcrEngine.recognize(buyuk ?: kirp) }.getOrDefault(emptyList())
            return QuestionParser.kutuMetni(
                items.sortedBy { it.bounds.left }.map { it.text to it.bounds.left..it.bounds.right }
            )
        } finally {
            if (buyuk != null && buyuk !== kirp) buyuk.recycle()
            if (kirp !== bmp) kirp.recycle()
        }
    }

    /** Bölgelerin dikey sınırlarına ayrıştırıcı için eklenen pay (parçaya oran). */
    private const val PAY = 0.01f
    private const val EN_AZ = 16
    private const val KUTU_PAYI = 0.15f
    private const val KUTU_ICI = 0.12f
    private const val KUTU_OLCEK = 3
}
