package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DebugScreen(vm: ArsivViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val history by vm.scanLog.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { ArsivTopBar("Teşhis", onBack = onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Tarama geçmişi (${history.size})") {
                    Text(
                        "Her tarama tek satır. Bir tur oynayıp buraya bakarsan hangi " +
                            "sorunun neden kaçtığı satır satır görünür.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.shareScanLog(context) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Paylaş") }
                        OutlinedButton(
                            onClick = { vm.clearScanLog() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Temizle") }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (history.isEmpty()) {
                        Text(
                            "Henüz tarama yapılmadı.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            history.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
            item {
                SectionCard("Bu ekran ne işe yarar?") {
                    Text(
                        "Uygulama en son taramada ekranda hangi metinleri gördüğünü ve " +
                            "bunlardan soru çıkarıp çıkaramadığını buraya yazar.\n\n" +
                            "Sorular yakalanmıyorsa: oyunu aç, bir soru ekranında birkaç saniye " +
                            "bekle, sonra buraya dön.\n\n" +
                            "• Liste boşsa uygulama metnini normal View'larla çizmiyor demektir — " +
                            "Ayarlar'dan OCR yedeğini aç.\n" +
                            "• Metinler görünüyor ama \"ayrıştırılamadı\" yazıyorsa, satırların " +
                            "yanındaki dikey konumlara bakıp Ayarlar'daki bölge oranlarını " +
                            "buna göre ayarla.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item {
                SectionCard("Son tarama") {
                    val dump = s.lastDebugDump.ifBlank { "Henüz tarama yapılmadı." }
                    Text(
                        dump,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
            item {
                SectionCard("Geçerli bölge ayarları") {
                    Column {
                        Text("Soru bölgesi:  %${(s.questionTop * 100).toInt()} – %${(s.questionBottom * 100).toInt()}",
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("Şık bölgesi:   %${(s.optionsTop * 100).toInt()} – %${(s.optionsBottom * 100).toInt()}",
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("Kayıt eşiği:   %${(s.minConfidence * 100).toInt()}",
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(
                            "Yüzdeler ekranın üstünden ölçülür. Yukarıdaki listedeki " +
                                "sayılar ise piksel cinsinden üst–alt konumlardır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
