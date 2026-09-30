package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.util.Exporters
import com.emre.bilbakalim.arsiv.util.GorselDisaAktarim

/** Dışa aktarma penceresindeki biçimler: ilk üçü ekran görüntüsü, gerisi metin. */
enum class DisaAktarimSecimi(
    val etiket: String,
    val aciklama: String,
    val gorsel: GorselDisaAktarim.Bicim? = null,
    val metin: Exporters.Format? = null
) {
    PDF(
        "PDF", "Ekran görüntüleri, doğru şık işaretli. Bir soru iki sayfaya bölünmez.",
        gorsel = GorselDisaAktarim.Bicim.PDF
    ),
    WORD(
        "Word (.docx)", "PDF ile aynı sayfalar, Word'de açılıp düzenlenebilir.",
        gorsel = GorselDisaAktarim.Bicim.WORD
    ),
    ZIP(
        "Fotoğraflar (ZIP)", "Her soru ayrı bir JPEG, doğru şık işaretli; yanında içindekiler listesi.",
        gorsel = GorselDisaAktarim.Bicim.ZIP
    ),
    JSON("Yedek (JSON)", "Geri yüklenebilen yedek. Görsel içermez.", metin = Exporters.Format.JSON),
    CSV("Tablo (CSV)", "Excel / Google E-Tablolar.", metin = Exporters.Format.CSV),
    ANKI("Anki (TSV)", "Anki'ye kart olarak.", metin = Exporters.Format.ANKI)
}

/**
 * Dışa aktarma: kategori seçimi (hepsi ya da örneğin yalnızca Matematik),
 * biçim ve görsellerde sayfa düzeni. Görsel dışa aktarım sürerken ilerleme,
 * kalan süre ve iptal; bitince paylaşım penceresi kendiliğinden açılıyor.
 *
 * @param kategori açılışta seçili tek kategori (Arşiv listesindeki süzgeç); null: hepsi.
 */
@Composable
fun DisaAktarPenceresi(
    vm: ArsivViewModel,
    varsayilan: DisaAktarimSecimi,
    kategori: String? = null,
    onKapat: () -> Unit
) {
    val durum by vm.disaAktarim.collectAsState()
    val d = durum
    if (d != null) {
        IlerlemePenceresi(d, vm, onKapat)
        return
    }

    val context = LocalContext.current
    val kategoriler by vm.categories.collectAsState()
    var secili by remember { mutableStateOf(kategori?.let { setOf(it) }) }
    var secim by remember { mutableStateOf(varsayilan) }
    var buyuk by remember { mutableStateOf(false) }
    var sayilar by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mesaj by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secili) {
        sayilar = null
        sayilar = vm.disaAktarimSayilari(secili)
    }
    val tumAnahtarlar = kategoriler.map { it.category ?: "" }.toSet()
    val adet = sayilar?.let { if (secim.gorsel != null) it.second else it.first }

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Dışa aktar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Kategoriler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                KutuSatiri("Tümü", secili == null) { secili = if (it) null else emptySet() }
                for (c in kategoriler) {
                    val anahtar = c.category ?: ""
                    KutuSatiri("${c.category ?: "Kategorisiz"} (${c.adet})", secili?.contains(anahtar) ?: true) { isaretli ->
                        val simdiki = secili ?: tumAnahtarlar
                        val yeni = if (isaretli) simdiki + anahtar else simdiki - anahtar
                        secili = if (yeni.containsAll(tumAnahtarlar)) null else yeni
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Biçim", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                for (s in DisaAktarimSecimi.entries) {
                    Row(
                        Modifier.fillMaxWidth().clickable { secim = s }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = secim == s, onClick = { secim = s })
                        Column {
                            Text(s.etiket, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                s.aciklama, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (secim == DisaAktarimSecimi.PDF || secim == DisaAktarimSecimi.WORD) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(checked = buyuk, onCheckedChange = { buyuk = it })
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (buyuk) "Büyük: sayfa başına ~1 soru" else "İki sütun: sayfa başına ~4 soru",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(ozet(secim, sayilar), style = MaterialTheme.typography.bodySmall)
                mesaj?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = adet != null && adet > 0,
                onClick = {
                    val g = secim.gorsel
                    val m = secim.metin
                    if (g != null) {
                        vm.gorselDisaAktar(context, secili, g, if (buyuk) 1 else 2)
                    } else if (m != null) {
                        vm.export(context, m, secili) { file ->
                            if (file == null) mesaj = "Dışa aktarılacak kayıt yok."
                            else {
                                Exporters.share(context, file, m)
                                onKapat()
                            }
                        }
                    }
                }
            ) { Text("Dışa aktar") }
        },
        dismissButton = { TextButton(onClick = onKapat) { Text("Vazgeç") } }
    )
}

private fun ozet(secim: DisaAktarimSecimi, sayilar: Pair<Int, Int>?): String {
    val (toplam, gorselli) = sayilar ?: return "Sayılıyor…"
    if (secim.gorsel == null) return "$toplam soru dışa aktarılacak."
    if (gorselli == 0) {
        return "Seçimdeki $toplam sorunun hiçbirinin ekran görüntüsü yok. Görüntü, Ayarlar'da " +
            "\"ekran görüntüsü kaydet\" açıkken yakalanan sorularda oluyor."
    }
    val (az, cok) = GorselDisaAktarim.tahminiSure(gorselli)
    val eksik = if (gorselli < toplam) " (${toplam - gorselli} sorunun ekran görüntüsü yok)" else ""
    return "$gorselli soru dışa aktarılacak$eksik. Tahmini süre ${sure(az)} – ${sure(cok)}: " +
        "ilk dışa aktarımda eski görüntülerin şıkları bir kez okunuyor, sonrakiler hızlı."
}

private fun sure(sn: Int): String = when {
    sn < 60 -> "$sn sn"
    else -> "${(sn + 30) / 60} dk"
}

@Composable
private fun KutuSatiri(metin: String, isaretli: Boolean, onDegis: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onDegis(!isaretli) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isaretli, onCheckedChange = onDegis)
        Text(metin, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun IlerlemePenceresi(
    d: ArsivViewModel.DisaAktarimDurumu,
    vm: ArsivViewModel,
    onKapat: () -> Unit
) {
    val context = LocalContext.current
    val kapat = {
        vm.disaAktarimKapat()
        onKapat()
    }
    val bitti = d.bitti
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                when {
                    d.hata != null -> "Dışa aktarılamadı"
                    bitti != null -> "Hazır"
                    else -> "Dışa aktarılıyor"
                }
            )
        },
        text = {
            Column {
                when {
                    d.hata != null -> Text(d.hata)
                    bitti != null -> Text(
                        "${bitti.adet} soru yazıldı; ${bitti.isaretli} tanesinde doğru şık görselde " +
                            "işaretli, diğerlerinde doğru cevap alt satırda yazılı." +
                            (if (bitti.atlanan > 0) " ${bitti.atlanan} ekran görüntüsü açılamadı." else "") +
                            "\n${bitti.dosya.name} · ${bitti.dosya.length() / 1024 / 1024} MB"
                    )
                    else -> {
                        val i = d.ilerleme
                        LinearProgressIndicator(
                            progress = { if (i == null || i.toplam == 0) 0f else i.yapilan.toFloat() / i.toplam },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (i == null) "Hazırlanıyor…"
                            else "${i.yapilan} / ${i.toplam}" + (i.kalanSn?.let { " · kalan ~${sure(it)}" } ?: "")
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (d.hata == null && bitti == null) {
                TextButton(onClick = { vm.disaAktarimIptal(); onKapat() }) { Text("İptal") }
            } else {
                TextButton(onClick = kapat) { Text("Tamam") }
            }
        },
        dismissButton = {
            if (bitti != null && bitti.adet > 0) {
                TextButton(onClick = { GorselDisaAktarim.paylas(context, bitti.dosya, bitti.bicim) }) {
                    Text("Yeniden paylaş")
                }
            }
        }
    )
}
