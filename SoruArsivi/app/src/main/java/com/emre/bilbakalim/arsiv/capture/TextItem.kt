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
    val depth: Int = 0,
    /**
     * OCR bloğunun kendi satırları, her biri kendi kutusuyla.
     *
     * ML Kit'in "blok"u bilerek geniş tutulmuş: çok satırlı bir soruyu tek
     * parçada topluyor ve bu bizim işimize geliyor. Ama şıkları kısa
     * sayılar olan sorularda aynı davranış tersine dönüyor — dördü tek
     * bloğa giriyor. Ayrıştırıcı böyle bir bloğu satırlarına ayırabilsin
     * diye satırlar burada saklanıyor. Erişilebilirlik yolunda boş.
     */
    val lines: List<TextItem> = emptyList()
) {
    val centerY: Int get() = bounds.centerY()
    val centerX: Int get() = bounds.centerX()
    val area: Int get() = maxOf(0, bounds.width()) * maxOf(0, bounds.height())
}
