package com.emre.bilbakalim.arsiv.capture

import android.graphics.Rect

/**
 * Ekrandan okunmuş tek bir metin parçası. Hem erişilebilirlik ağacından
 * hem de OCR'dan aynı biçimde üretilir; ayrıştırıcı ikisini de aynı şekilde işler.
 */
data class TextItem(
    val text: String,
    val bounds: Rect,
    /** Erişilebilirlik yolunda: düğüm tıklanabilir mi (şık olma ihtimali yüksek). */
    val clickable: Boolean = false,
    /** Görsel yığın derinliği; kırılma durumunda sıralama için. */
    val depth: Int = 0
) {
    val centerY: Int get() = bounds.centerY()
    val centerX: Int get() = bounds.centerX()
    val area: Int get() = maxOf(0, bounds.width()) * maxOf(0, bounds.height())
}
