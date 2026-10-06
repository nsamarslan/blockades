package com.emre.bilbakalim.arsiv.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.data.BenzerKayitlar
import com.emre.bilbakalim.arsiv.data.QuestionEntity

/**
 * Benzer kayıtları birleştirme (bkz. [BenzerKayitlar]): konu seçilir,
 * adaylar bulunup yapay zekâya sorulur, aynı denen çiftlerde hangisinin
 * tutulacağını sen seçersin.
 */
@Composable
fun BenzerKayitlarScreen(vm: ArsivViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val kategoriler by vm.categories.collectAsState()
    val durum by vm.benzerDurum.collectAsState()
    val ciftler by vm.benzerCiftler.collectAsState()
    val gecmis by vm.benzerGecmis.collectAsState()
    var kategori by remember { mutableStateOf<String?>(null) }
    var etiketsiz by remember { mutableStateOf(false) }
    fun bildir(m: String?) { m?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    Scaffold(topBar = { ArsivTopBar("Benzer kayıtlar", onBack = onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Aynı sorunun kopyalarını bul", Icons.Default.MergeType) {
                    Text(
                        "Aynı soru ekrandan farklı okunup iki kez kaydedilmiş olabilir " +
                            "(\"ldea\" / \"Idea\"). Soru metni benzer ve şıklarının en az üçü " +
                            "ortak olan kayıtlar yapay zekâya \"aynı soru mu\" diye sorulur; " +
                            "aynı dediklerinde hangisini tutacağını sen seçersin. Aynı kelime " +
                            "farklı şıklarla gelebildiği için (\"Concrete\": Beton / Somut) " +
                            "şıkları farklı kayıtlar aday bile olmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    KonuSecici(kategoriler, kategori, etiketsiz) { k, e -> kategori = k; etiketsiz = e }
                    Spacer(Modifier.height(8.dp))
                    if (durum.calisiyor) {
                        OutlinedButton(onClick = { vm.benzerDurdur() }, modifier = Modifier.fillMaxWidth()) { Text("Durdur") }
                    } else {
                        Button(onClick = { vm.benzerBul(kategori, etiketsiz) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Benzerleri bul")
                        }
                    }
                    if (gecmis.isNotEmpty()) {
                        TextButton(onClick = { vm.benzerGeriAl { bildir(it) } }) {
                            Text("Son birleştirmeyi geri al (${gecmis.size})")
                        }
                    }
                    TextButton(onClick = { vm.benzerEleneniUnut(); bildir("Daha önce \"ayrı\" denen çiftler yeniden sorulacak.") }) {
                        Text("\"Ayrı\" kararlarını unut")
                    }
                }
            }
            durum.mesaj?.let { m ->
                item {
                    SectionCard {
                        if (durum.aday > 0) {
                            Text("${durum.sorulan} / ${durum.aday} aday soruldu · ${durum.farkliBulunan} çift farklı çıktı",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(m, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(ciftler, key = { "${it.a.id}-${it.b.id}" }) { c ->
                CiftKarti(
                    c,
                    tut = { id -> vm.benzerBirlestir(c, id) { bildir(it ?: "Birleştirildi.") } },
                    ayri = { vm.benzerAyriBirak(c) }
                )
            }
        }
    }
}

@Composable
private fun CiftKarti(c: BenzerKayitlar.Cift, tut: (Long) -> Unit, ayri: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when {
                    c.groq == true && c.gemini == true -> "İki yapay zekâ da aynı soru dedi"
                    c.ikisiDeAyni -> "Yapay zekâ aynı soru dedi"
                    else -> "Yapay zekâlar ayrıştı (biri aynı, biri farklı dedi)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (c.ikisiDeAyni) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            val cevaplarFarkli = c.a.correctText != null && c.b.correctText != null &&
                com.emre.bilbakalim.arsiv.util.TurkishText.normalizeKey(c.a.correctText!!) !=
                com.emre.bilbakalim.arsiv.util.TurkishText.normalizeKey(c.b.correctText!!)
            if (cevaplarFarkli) {
                Text(
                    "Dikkat: cevapları farklı. Tuttuğunun cevabı kalır.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            Kayit("1", c.a)
            Spacer(Modifier.height(6.dp))
            Kayit("2", c.b)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { tut(c.a.id) }, modifier = Modifier.weight(1f)) { Text("1'i tut") }
                OutlinedButton(onClick = { tut(c.b.id) }, modifier = Modifier.weight(1f)) { Text("2'yi tut") }
            }
            TextButton(onClick = ayri, modifier = Modifier.fillMaxWidth()) { Text("Ayrı sorular, dokunma") }
        }
    }
}

@Composable
private fun Kayit(no: String, q: QuestionEntity) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("$no) ${q.questionText}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            q.options.forEachIndexed { i, o ->
                Text(
                    "${optionLetter(i)}) $o" + if (q.correctIndex == i) "  ✓" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (q.correctIndex == i) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                "cevap: ${q.answerSource ?: "yok"} · ${q.seenCount} kez çıktı",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
