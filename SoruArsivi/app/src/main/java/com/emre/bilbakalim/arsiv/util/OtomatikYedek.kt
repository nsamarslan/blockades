package com.emre.bilbakalim.arsiv.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.emre.bilbakalim.arsiv.data.AnahtarKotasi
import com.emre.bilbakalim.arsiv.data.Prefs
import com.emre.bilbakalim.arsiv.data.Repo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Günlük otomatik yedek.
 *
 * Arşiv günde bir kez, dışa aktarmayla aynı JSON biçiminde (içe aktar
 * düğmesiyle geri yüklenebilir) telefonun **İndirilenler/SoruArsivi/Yedekler**
 * klasörüne yazılıyor; son [SAKLANAN] gün tutuluyor. Toplu bir içe aktarma ya
 * da düzeltme arşivi bozarsa bir önceki güne dönülebilsin diye.
 *
 * Android 9 ve altında İndirilenler'e izinsiz yazılamadığı için uygulamanın
 * kendi klasörüne (Android/data/.../files/yedekler) yazılıyor.
 */
object OtomatikYedek {

    data class Son(val zaman: Long, val yer: String, val adet: Int)

    private const val TAG = "SoruArsivi/Yedek"
    private const val KLASOR = "SoruArsivi/Yedekler"
    private const val ON_EK = "soru_arsivi_yedek_"
    /** Kaç günün yedeği tutulsun. */
    const val SAKLANAN = 7

    private val kilit = Mutex()
    private val _son = MutableStateFlow<Son?>(null)
    val son: StateFlow<Son?> = _son

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("otomatik_yedek", Context.MODE_PRIVATE)

    fun yukle(context: Context) {
        val p = sp(context)
        val z = p.getLong("zaman", 0L)
        if (z > 0L) _son.value = Son(z, p.getString("yer", "") ?: "", p.getInt("adet", 0))
    }

    /** Ayar açıksa ve bugün yedek alınmadıysa yedekler. */
    suspend fun gerekirseYedekle(context: Context) {
        if (!Prefs.get(context).state.value.otomatikYedek) return
        val gun = sp(context).getString("gun", null)
        if (gun == AnahtarKotasi.bugun()) return
        yedekle(context)
    }

    /**
     * Hemen yedekler. Başarılıysa yerini, değilse sebebini döndürür. [ek]
     * verilirse ("birlestirme_oncesi") dosya adına eklenir ve günlük yedek
     * sayılmaz.
     */
    suspend fun yedekle(context: Context, ek: String? = null): Result<Son> = withContext(Dispatchers.IO) {
        kilit.withLock {
            runCatching {
                val rows = Repo.get(context).allForExport()
                require(rows.isNotEmpty()) { "arşiv boş" }
                val gun = AnahtarKotasi.bugun()
                val ad = "$ON_EK$gun${ek?.let { "_$it" } ?: ""}.json"
                val metin = Exporters.jsonMetni(rows)
                val yer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    indirilenlereYaz(context, ad, metin)
                    budaIndirilenler(context)
                    "İndirilenler/$KLASOR"
                } else {
                    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "yedekler").apply { mkdirs() }
                    File(dir, ad).writeText(metin, Charsets.UTF_8)
                    dir.listFiles { f -> f.name.startsWith(ON_EK) }
                        ?.sortedByDescending { it.name }
                        ?.drop(SAKLANAN)
                        ?.forEach { it.delete() }
                    dir.absolutePath
                }
                val son = Son(System.currentTimeMillis(), yer, rows.size)
                if (ek != null) return@runCatching son
                sp(context).edit()
                    .putString("gun", gun)
                    .putLong("zaman", son.zaman)
                    .putString("yer", son.yer)
                    .putInt("adet", son.adet)
                    .apply()
                _son.value = son
                Log.i(TAG, "Yedek alındı: $yer/$ad (${rows.size} soru)")
                son
            }.onFailure { Log.w(TAG, "Yedek alınamadı: ${it.message}") }
        }
    }

    private fun yol() = "${Environment.DIRECTORY_DOWNLOADS}/$KLASOR/"

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun indirilenlereYaz(context: Context, ad: String, metin: String) {
        val cr = context.contentResolver
        val koleksiyon = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Aynı gün ikinci kez ("şimdi yedekle") alınırsa eskisinin yerine.
        cr.query(
            koleksiyon, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(yol(), ad), null
        )?.use { c ->
            while (c.moveToNext()) {
                runCatching { cr.delete(ContentUris.withAppendedId(koleksiyon, c.getLong(0)), null, null) }
            }
        }
        val uri = cr.insert(koleksiyon, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ad)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, yol())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("İndirilenler'e yazılamadı")
        try {
            cr.openOutputStream(uri)?.use { it.write(metin.toByteArray(Charsets.UTF_8)) }
                ?: error("dosya açılamadı")
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { cr.delete(uri, null, null) }
            throw t
        }
    }

    /** Son [SAKLANAN] günden eskileri siler (yalnızca uygulamanın kendi yazdıkları). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun budaIndirilenler(context: Context) {
        val cr = context.contentResolver
        val koleksiyon = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val idler = ArrayList<Pair<Long, String>>()
        cr.query(
            koleksiyon, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(yol(), "$ON_EK%"), null
        )?.use { c ->
            while (c.moveToNext()) idler += c.getLong(0) to c.getString(1)
        }
        // Ad tarih içerdiği için ada göre sıralamak tarihe göre sıralamak.
        idler.sortedByDescending { it.second }.drop(SAKLANAN).forEach { (id, _) ->
            // Uygulama silinip kurulduysa eski dosyalar artık bizim değil;
            // onlara dokunamıyoruz, sorun değil.
            runCatching { cr.delete(ContentUris.withAppendedId(koleksiyon, id), null, null) }
        }
    }
}
