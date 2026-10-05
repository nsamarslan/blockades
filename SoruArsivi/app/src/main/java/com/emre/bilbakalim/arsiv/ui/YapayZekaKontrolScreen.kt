package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.data.TopluKontrol

/**
 * Arşivi yapay zekâyla toplu kontrol (bkz. [TopluKontrol]): kategori ve
 * cevap durumu seçilir, sorular iki yapay zekâya sorulur; aynı derlerse
 * kaydedilir, farklı derlerse aşağıdaki listede sen seçersin.
 */
@Composable
fun YapayZekaKontrolScreen(vm: ArsivViewModel, onBack: () -> Unit) {
    val kategoriler by vm.categories.collectAsState()
    val durum by vm.topluDurum.collectAsState()
    val bekleyenler by vm.topluBekleyenler.collectAsState()

    // Seçim: null ve etiketsiz=false → bütün arşiv.
    var kategori by remember { mutableStateOf<String?>(null) }
    var etiketsiz by remember { mutableStateOf(false) }
    var cevap by remember { mutableStateOf(1) }
    var menu by remember { mutableStateOf(false) }
    var sayi by remember { mutableStateOf<Int?>(null) }
    var temizleSor by remember { mutableStateOf(false) }

    LaunchedEffect(kategori, etiketsiz, cevap, durum.calisiyor) {
        sayi = null
        sayi = vm.kontrolSayisi(kategori, etiketsiz, cevap)
    }

    if (temizleSor) {
        AlertDialog(
            onDismissRequest = { temizleSor = false },
            title = { Text("Liste temizlensin mi?") },
            text = { Text("Bekleyen ${bekleyenler.size} soru olduğu gibi kalır; listeden çıkar.") },
            confirmButton = { TextButton(onClick = { vm.topluListeyiTemizle(); temizleSor = false }) { Text("Temizle") } },
            dismissButton = { TextButton(onClick = { temizleSor = false }) { Text("Vazgeç") } }
        )
    }

    Scaffold(topBar = { ArsivTopBar("Yapay zekâyla kontrol", onBack = onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Ne kontrol edilsin?", Icons.Default.SmartToy) {
                    Text(
                        "Sorular hem Groq'a hem Gemini'ye sorulur. İkisi aynı şıkkı " +
                            "derse cevap kaydedilir (oyunda hiç görülmemiş yanlış cevap " +
                            "da düzeltilir). Farklı derlerse soru aşağıdaki listeye düşer, " +
                            "sen seçersin. Oyunda renkten görülen ya da elle seçtiğin " +
                            "cevap kendiliğinden değiştirilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Konu", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when {
                                    etiketsiz -> "Etiketsiz"
                                    kategori == null -> "Bütün arşiv"
                                    else -> kategori!!
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Bütün arşiv") },
                                onClick = { kategori = null; etiketsiz = false; menu = false }
                            )
                            kategoriler.filter { it.adet > 0 }.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.category ?: "Etiketsiz"} (${c.adet})") },
                                    onClick = {
                                        kategori = c.category
                                        etiketsiz = c.category == null
                                        menu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Hangi sorular", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = cevap == 1, onClick = { cevap = 1 }, label = { Text("Cevabı olmayan") })
                        FilterChip(selected = cevap == 2, onClick = { cevap = 2 }, label = { Text("Cevabı olan") })
                        FilterChip(selected = cevap == 0, onClick = { cevap = 0 }, label = { Text("Hepsi") })
                    }
                    Text(
                        sayi?.let { "$it soru · ~${(it + 24) / 25} istek her yapay zekâya" } ?: "sayılıyor…",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    if (durum.calisiyor) {
                        OutlinedButton(onClick = { vm.topluDurdur() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Durdur")
                        }
                    } else {
                        Button(
                            onClick = { vm.topluBaslat(kategori, etiketsiz, cevap) },
                            enabled = (sayi ?: 0) > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Kontrolü başlat") }
                    }
                    Text(
                        "Oyun oynarken yapay zekâ kotası bununla paylaşılır. Ekrandan " +
                            "çıksan da kontrol sürer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (durum.toplam > 0 || durum.mesaj != null) {
                item {
                    SectionCard("Durum") {
                        if (durum.toplam > 0) {
                            LinearProgressIndicator(
                                progress = { durum.islenen / durum.toplam.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("${durum.islenen} / ${durum.toplam} soru", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Kaydedilen ${durum.kaydedilen} · düzeltilen ${durum.duzeltilen} · " +
                                    "doğrulanan ${durum.ayni} · karar sende ${durum.listeye}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        durum.mesaj?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (bekleyenler.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "Kararını bekleyen ${bekleyenler.size} soru",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).padding(top = 12.dp)
                        )
                        TextButton(onClick = { temizleSor = true }) { Text("Temizle") }
                    }
                    Text(
                        "Doğru şıkka dokun; kayda elle seçilmiş cevap olarak yazılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(bekleyenler, key = { it.id }) { b -> BekleyenKart(b) { i -> vm.topluSec(b, i) } }
            }
        }
    }
}

@Composable
private fun BekleyenKart(b: TopluKontrol.Bekleyen, sec: (Int?) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(b.soru, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(b.neden, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            b.siklar.forEachIndexed { i, s ->
                val etiketler = listOfNotNull(
                    "Groq".takeIf { b.groq == i },
                    "Gemini".takeIf { b.gemini == i },
                    "kayıtlı".takeIf { b.kayitli == i }
                )
                OutlinedButton(
                    onClick = { sec(i) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("${optionLetter(i)}) $s", modifier = Modifier.weight(1f))
                    if (etiketler.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            etiketler.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            TextButton(onClick = { sec(null) }) { Text("Değiştirme, listeden çıkar") }
        }
    }
}
