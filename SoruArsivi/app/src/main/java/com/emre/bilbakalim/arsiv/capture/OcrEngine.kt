package com.emre.bilbakalim.arsiv.capture

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit metin tanıma — tamamen cihaz üzerinde, internetsiz çalışır.
 * Latin modeli Türkçe'nin ı/ğ/ş/ö/ç/ü harflerini tanır; model APK'nın
 * içinde geldiği için ilk açılışta indirme beklenmez.
 */
object OcrEngine {

    private const val TAG = "SoruArsivi/OCR"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Görüntüdeki metin bloklarını konumlarıyla döndürür.
     * Blok kullanıyoruz çünkü ML Kit çok satırlı bir soruyu tek blokta toplar —
     * bu da satır satır birleştirme derdini büyük ölçüde ortadan kaldırır.
     */
    suspend fun recognize(bitmap: Bitmap): List<TextItem> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val out = ArrayList<TextItem>(text.textBlocks.size)
                        for (block in text.textBlocks) {
                            val b: Rect = block.boundingBox ?: continue
                            val t = block.text.trim()
                            if (t.isEmpty()) continue
                            // Satırlar da taşınıyor: ML Kit alt alta duran
                            // kısa şıkları tek bloğa toplayabiliyor ve o
                            // bloğu ancak satırlarına ayırarak şıklara
                            // geri çevirebiliyoruz.
                            val lines = block.lines.mapNotNull { line ->
                                val lb = line.boundingBox ?: return@mapNotNull null
                                val lt = line.text.trim()
                                if (lt.isEmpty()) return@mapNotNull null
                                // Satırın kelimeleri de taşınıyor: tur sonu
                                // ekranında alttaki düğme yazıları ("Ana Menü",
                                // "Tekrar Oyna") tek satıra birleşiyor ve
                                // düğmenin yeri ancak kelimelerden bulunuyor.
                                val kelimeler = line.elements.mapNotNull { el ->
                                    val eb = el.boundingBox ?: return@mapNotNull null
                                    val et = el.text.trim()
                                    if (et.isEmpty()) null else TextItem(et, Rect(eb))
                                }
                                TextItem(lt, Rect(lb), clickable = false, lines = kelimeler)
                            }
                            out.add(TextItem(t, Rect(b), clickable = false, lines = lines))
                        }
                        if (cont.isActive) cont.resume(out)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "OCR başarısız: ${e.message}")
                        if (cont.isActive) cont.resume(emptyList())
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "OCR hatası: ${t.message}")
                if (cont.isActive) cont.resume(emptyList())
            }
        }

    /** Kaynakları serbest bırakır (servis kapanırken çağrılır). */
    fun close() {
        runCatching { recognizer.close() }
    }
}
