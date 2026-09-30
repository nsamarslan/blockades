package com.emre.bilbakalim.arsiv.util

/**
 * Soru görsellerini sayfalara dizer: satır satır, [sutun] sütun.
 *
 * Bir satır sayfada kalan yere sığmıyorsa yeni sayfaya geçiliyor; bir soru
 * hiçbir zaman iki sayfaya bölünmüyor. Sayfadan uzun bir görsel sayfaya
 * sığacak kadar küçültülüyor.
 *
 * Ölçüler PDF noktası (1/72 inç); y sayfanın üstünden aşağı doğru.
 * Android'e bağlı değil; birim testte denenebiliyor.
 */
class Sayfalayici(
    private val sayfaW: Float,
    private val sayfaH: Float,
    private val kenar: Float,
    private val aralik: Float,
    private val sutun: Int
) {
    /** Bir görselin sayfadaki yeri. */
    data class Yer(val sayfa: Int, val x: Float, val y: Float, val w: Float, val h: Float)

    /** Bir sütunun genişliği. */
    val sutunW: Float = (sayfaW - 2 * kenar - (sutun - 1) * aralik) / sutun
    private val icerikH = sayfaH - 2 * kenar

    private var sayfa = 0
    private var imlec = kenar
    private var sayfadaVar = false

    /**
     * Bir satırı (en fazla [sutun] görsel, piksel boyutlarıyla) sıradaki
     * yere koyar. Satırdaki görsellerin hepsi aynı sayfaya düşer.
     */
    fun satir(boyutlar: List<Pair<Int, Int>>): List<Yer> {
        require(boyutlar.size in 1..sutun) { "satırda ${boyutlar.size} görsel, en fazla $sutun" }
        val olcekli = boyutlar.map { (w, h) -> sigdir(w, h, sutunW, icerikH) }
        val satirH = olcekli.maxOf { it.second }
        if (sayfadaVar && imlec + satirH > sayfaH - kenar + 0.01f) {
            sayfa++
            imlec = kenar
            sayfadaVar = false
        }
        val yerler = olcekli.mapIndexed { i, (w, h) ->
            Yer(sayfa, kenar + i * (sutunW + aralik) + (sutunW - w) / 2, imlec, w, h)
        }
        imlec += satirH + aralik
        sayfadaVar = true
        return yerler
    }

    companion object {
        /** A4, nokta cinsinden. */
        const val A4_W = 595.28f
        const val A4_H = 841.89f

        /** En-boy oranını koruyarak [maxW] x [maxH] kutusuna sığdırır. */
        fun sigdir(pxW: Int, pxH: Int, maxW: Float, maxH: Float): Pair<Float, Float> {
            val s = minOf(maxW / pxW.coerceAtLeast(1), maxH / pxH.coerceAtLeast(1))
            return pxW * s to pxH * s
        }
    }
}
