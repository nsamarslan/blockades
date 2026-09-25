package com.emre.bilbakalim.arsiv.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.capture.OptionBoxFinder
import com.emre.bilbakalim.arsiv.data.EkranBolgesi
import kotlinx.coroutines.launch

private enum class Cizim { SORU, SIK }

private val SORU_RENGI = Color(0xFFFFB300)
private val SIK_RENGI = Color(0xFF43A047)
private val KUTU_RENGI = Color(0xFF00B8D4)

/**
 * "Ekranı ayarla": oyunun bir karesi üstünde soru ve şık bölgelerini
 * parmakla dikdörtgen çizerek seçme.
 *
 * Neden: uygulama telefonun ekranına göre yazıldı. Başka bir telefonda ya
 * da tablette (özellikle yatay tutulunca) soru kartı ve şıklar ekranın başka
 * bir oranında duruyor. Şık kutuları zaten ekrandan ölçülüyor, ama ölçünün
 * nereye bakacağını ve soru metninin nerede aranacağını elle söylemek her
 * cihazda işe yarayan tek yol.
 */
@Composable
fun EkranAyarlaScreen(vm: ArsivViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()

    var kare by remember { mutableStateOf<AyarKaresi?>(null) }
    var yukleniyor by remember { mutableStateOf(true) }
    /** Kareyi yeniden yüklemek için. */
    var yenile by remember { mutableIntStateOf(0) }
    var galeridenSecildi by remember { mutableStateOf(false) }

    var soru by remember { mutableStateOf(settings.soruBolgesi) }
    var sik by remember { mutableStateOf(settings.sikBolgesi) }
    var mod by remember { mutableStateOf(if (settings.soruBolgesi == null) Cizim.SORU else Cizim.SIK) }
    var kutular by remember { mutableStateOf<List<OptionBoxFinder.Box>>(emptyList()) }

    LaunchedEffect(yenile) {
        if (galeridenSecildi) return@LaunchedEffect
        yukleniyor = true
        kare = vm.ayarKaresi(context)
        yukleniyor = false
    }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val secilen = vm.galeridenKare(context, uri)
            if (secilen == null) {
                Toast.makeText(context, "Görüntü açılamadı", Toast.LENGTH_SHORT).show()
            } else {
                galeridenSecildi = true
                kare = secilen
            }
        }
    }

    // Şık bölgesi hiç seçilmemişse kutu ölçümünün kendi bulduğu yer öneri
    // olarak gösteriliyor; kullanıcı onaylar ya da yeniden çizer.
    LaunchedEffect(kare) {
        val k = kare ?: return@LaunchedEffect
        if (sik == null) {
            val bulunan = vm.kutulariDene(k.bitmap, null)
            if (bulunan.isNotEmpty()) {
                val w = k.bitmap.width.toFloat()
                val h = k.bitmap.height.toFloat()
                sik = EkranBolgesi(
                    bulunan.minOf { it.left } / w, bulunan.minOf { it.top } / h,
                    bulunan.maxOf { it.right } / w, bulunan.maxOf { it.bottom } / h
                )
            }
        }
    }
    // Seçilen şık bölgesinde ölçüm deneniyor: kaydetmeden önce işe yarayıp
    // yaramadığı görülsün.
    LaunchedEffect(kare, sik) {
        val k = kare ?: return@LaunchedEffect
        kutular = vm.kutulariDene(k.bitmap, sik)
    }

    Scaffold(topBar = { ArsivTopBar("Ekranı ayarla", onBack = onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when (mod) {
                    Cizim.SORU -> "Soru: numara kutusunun (\"1.\") üstünden beyaz soru kartının " +
                        "altına kadar, kartın tam genişliğinde çiz. Üstteki yıldız, altın ve " +
                        "1-7 şeridi dışarıda kalsın."
                    Cizim.SIK -> "Şıklar: ilk şıkkın üst kenarından son şıkkın alt kenarına kadar, " +
                        "hapların tam genişliğinde çiz. Alttaki jokerler (50/50, x2) dışarıda kalsın."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mod == Cizim.SORU,
                    onClick = { mod = Cizim.SORU },
                    label = { Text(if (soru == null) "Soru" else "Soru ✓") },
                    leadingIcon = { RenkNoktasi(SORU_RENGI) }
                )
                FilterChip(
                    selected = mod == Cizim.SIK,
                    onClick = { mod = Cizim.SIK },
                    label = { Text(if (sik == null) "Şıklar" else "Şıklar ✓") },
                    leadingIcon = { RenkNoktasi(SIK_RENGI) }
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val k = kare
                when {
                    k != null -> KareUstundeCizim(
                        kare = k,
                        soru = soru,
                        sik = sik,
                        kutular = kutular,
                        mod = mod,
                        onCizildi = { bolge ->
                            when (mod) {
                                Cizim.SORU -> {
                                    soru = bolge
                                    // Soru çizildi: sıra şıklarda.
                                    if (sik == null) mod = Cizim.SIK
                                }
                                Cizim.SIK -> sik = bolge
                            }
                        }
                    )
                    yukleniyor -> Text("Kare yükleniyor…")
                    else -> Text(
                        "Henüz oyunun bir karesi yok.\n\nOyunu aç, bir soru ekrandayken birkaç " +
                            "saniye bekle ve buraya dön. Ya da oyunda bir soru ekrandayken " +
                            "ekran görüntüsü alıp aşağıdan galeriden seç.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            kare?.let { k ->
                Text(
                    buildString {
                        append("Kare: ").append(k.kaynak).append(" · ")
                        append(
                            when {
                                sik == null -> "şık bölgesi seçilmedi"
                                kutular.size >= 3 -> "şık bölgesinde ${kutular.size} şık kutusu bulundu ✓"
                                else -> "şık bölgesinde şık kutusu bulunamadı — hapların kenarından çiz"
                            }
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sik != null && kutular.size < 3) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { galeri.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Galeriden")
                }
                OutlinedButton(
                    onClick = { galeridenSecildi = false; yenile++ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Yenile")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                TextButton(
                    onClick = {
                        vm.resetRegions()
                        soru = null
                        sik = null
                        mod = Cizim.SORU
                        Toast.makeText(context, "Bölgeler ekrandan kendiliğinden bulunacak", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Otomatiğe dön") }
                Button(
                    onClick = {
                        val q = soru
                        val o = sik
                        if (q != null && o != null) {
                            vm.setEkranBolgeleri(q, o)
                            Toast.makeText(context, "Ekran bölgeleri kaydedildi", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    enabled = soru != null && sik != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Kaydet", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Kareyi en-boy oranını koruyarak sığdırır ve üstüne bölgeleri çizer.
 * Parmakla sürükleme o anki moddaki bölgeyi yeniden çizer.
 */
@Composable
private fun KareUstundeCizim(
    kare: AyarKaresi,
    soru: EkranBolgesi?,
    sik: EkranBolgesi?,
    kutular: List<OptionBoxFinder.Box>,
    mod: Cizim,
    onCizildi: (EkranBolgesi) -> Unit
) {
    val bmp = kare.bitmap
    val resim = remember(bmp) { bmp.asImageBitmap() }
    val oran = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
    var bas by remember { mutableStateOf<Offset?>(null) }
    var simdi by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val w = minOf(maxW, maxH * oran)
        val h = w / oran
        Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
            Image(
                bitmap = resim,
                contentDescription = "Oyun karesi",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                Modifier.fillMaxSize().pointerInput(mod) {
                    detectDragGestures(
                        onDragStart = { bas = it; simdi = it },
                        onDrag = { change, _ -> simdi = change.position },
                        onDragEnd = {
                            val a = bas
                            val b = simdi
                            if (a != null && b != null) {
                                val bolge = EkranBolgesi.ikiNoktadan(
                                    a.x / size.width, a.y / size.height,
                                    b.x / size.width, b.y / size.height
                                )
                                if (bolge.gecerli) onCizildi(bolge)
                            }
                            bas = null
                            simdi = null
                        },
                        onDragCancel = { bas = null; simdi = null }
                    )
                }
            ) {
                soru?.let { bolgeCiz(it, SORU_RENGI) }
                sik?.let { bolgeCiz(it, SIK_RENGI) }
                // Bulunan şık kutuları: seçimin işe yaradığı görülsün.
                val sx = size.width / bmp.width
                val sy = size.height / bmp.height
                kutular.forEach { k ->
                    drawRect(
                        KUTU_RENGI,
                        topLeft = Offset(k.left * sx, k.top * sy),
                        size = Size(k.width * sx, k.height * sy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                val a = bas
                val b = simdi
                if (a != null && b != null) {
                    val renk = if (mod == Cizim.SORU) SORU_RENGI else SIK_RENGI
                    drawRect(
                        renk.copy(alpha = 0.25f),
                        topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
                    )
                    drawRect(
                        renk,
                        topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun DrawScope.bolgeCiz(b: EkranBolgesi, renk: Color) {
    val ust = Offset(b.sol * size.width, b.ust * size.height)
    val boyut = Size(b.genislik * size.width, b.yukseklik * size.height)
    drawRect(renk.copy(alpha = 0.18f), topLeft = ust, size = boyut)
    drawRect(renk, topLeft = ust, size = boyut, style = Stroke(width = 3.dp.toPx()))
}

@Composable
private fun RenkNoktasi(renk: Color) {
    Canvas(Modifier.size(12.dp)) { drawCircle(renk) }
}
