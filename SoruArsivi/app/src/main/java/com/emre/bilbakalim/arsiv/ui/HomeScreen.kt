package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emre.bilbakalim.arsiv.data.Prefs

@Composable
fun HomeScreen(
    vm: ArsivViewModel,
    onOpenList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    onPickApp: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val total by vm.total.collectAsState()
    val answered by vm.answered.collectAsState()
    val categories by vm.categories.collectAsState()
    val recent by vm.recent.collectAsState()
    val fastOn by vm.fastCaptureOn.collectAsState()

    // Sistem ayarlarından dönünce durumu tazele.
    var a11yOn by remember { mutableStateOf(ArsivViewModel.accessibilityEnabled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yOn = ArsivViewModel.accessibilityEnabled(context)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val hazir = a11yOn && settings.targetPackages.isNotEmpty() && !settings.paused

    Scaffold(
        topBar = {
            ArsivTopBar("Soru Arşivi") {
                IconButton(onClick = onOpenDebug) {
                    Icon(Icons.Default.BugReport, contentDescription = "Teşhis")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Durum ----------------------------------------------------
            item {
                SectionCard(
                    container = if (hazir) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hazir) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (hazir) "Yakalama açık" else "Yakalama kapalı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    !a11yOn -> "Erişilebilirlik servisi kapalı"
                                    settings.targetPackages.isEmpty() -> "Hedef uygulama seçilmedi"
                                    settings.paused -> "Elle duraklatıldı"
                                    settings.autoPlay ->
                                        "Oyunu aç ve bırak — uygulama kendi oynayıp soruları toplayacak"
                                    else -> "Oyunu aç ve oyna — sorular kendiliğinden kaydedilecek"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (a11yOn && settings.targetPackages.isNotEmpty()) {
                            IconButton(onClick = { vm.setPaused(!settings.paused) }) {
                                Icon(
                                    if (settings.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (settings.paused) "Devam et" else "Duraklat"
                                )
                            }
                        }
                    }

                    if (!a11yOn) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { ArsivViewModel.openAccessibilitySettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Erişilebilirlik ayarlarını aç") }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Açılan listede \"Soru Arşivi\"ni bulup aç. Android bunu " +
                                "\"indirilen uygulamalar\" veya \"yüklü servisler\" başlığı altında gösterir.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (settings.targetPackages.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Apps, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Hangi uygulamayı izleyeyim?")
                        }
                    }
                }
            }

            // ---- Mod seçimi -----------------------------------------------
            item {
                SectionCard(
                    container = if (settings.autoPlay)
                        MaterialTheme.colorScheme.tertiaryContainer else null
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (settings.autoPlay) Icons.Default.SmartToy else Icons.Default.TouchApp,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (settings.autoPlay) "Otomatik mod" else "Manuel mod",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (settings.autoPlay)
                                    "Uygulama şıklardan birini rastgele seçip basıyor, " +
                                        "tur bitince \"Tekrar Oyna\"ya dokunuyor."
                                else "Oyunu sen oynuyorsun; uygulama sadece okuyup kaydediyor.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !settings.autoPlay,
                            onClick = { vm.setAutoPlay(false) },
                            label = { Text("Manuel") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.autoPlay,
                            onClick = { vm.setAutoPlay(true) },
                            label = { Text("Otomatik") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (settings.autoPlay) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Otomatik modda uygulama ekrana dokunur. Telefonu bırakıp " +
                                "gidebilirsin; oyun ekranından çıkarsan kendiliğinden durur. " +
                                "İstediğin an Manuel'e dönebilir ya da yukarıdaki duraklat " +
                                "düğmesine basabilirsin.",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (!fastOn) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Hızlı yakalama kapalıyken bot yavaş çalışır ve bazı " +
                                    "cevapları kaçırır — aşağıdan açman önerilir.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ---- Hızlı yakalama -------------------------------------------
            item {
                SectionCard(
                    container = if (fastOn) null
                                else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (fastOn) "Hızlı yakalama açık" else "Hızlı yakalama kapalı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (fastOn) "Ekran karesine saniyede 20 kez bakılabiliyor."
                                else "Saniyede yalnızca 1 kare alınabiliyor.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (!fastOn) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Android, erişilebilirlik ekran görüntüsünü saniyede bir " +
                                "kereden fazla almaya izin vermiyor. Şıklar ekrana teker " +
                                "teker geliyor ve cevap yarım saniyede açılıp geçiyor — " +
                                "bu hızda ikisi de kaçabiliyor.\n\nEkran yansıtmayı " +
                                "açarsan uygulama kareye istediği an bakabilir. Görüntü " +
                                "telefondan dışarı çıkmaz.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { ArsivViewModel.startFastCapture(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Hızlı yakalamayı aç") }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Telefonu yeniden başlattığında kapanır, tekrar açman gerekir.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { ArsivViewModel.stopFastCapture(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Hızlı yakalamayı durdur") }
                    }
                }
            }

            // ---- Sayılar --------------------------------------------------
            item {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBox("toplam soru", total.toString(), Modifier.weight(1f))
                        StatBox("cevabı bilinen", answered.toString(), Modifier.weight(1f))
                        StatBox(
                            "cevabı eksik",
                            (total - answered).coerceAtLeast(0).toString(),
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenList, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Arşivi aç")
                    }
                }
            }

            // ---- Aktif kategori -------------------------------------------
            item {
                SectionCard("Kaydedilecek kategori") {
                    Text(
                        "Yeni sorular bu etiketle kaydedilir. Uygulama kategori adını " +
                            "ekranda görürse zaten kendisi yazar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Prefs.BILINEN_KATEGORILER) { cat ->
                            FilterChip(
                                selected = settings.activeCategory == cat,
                                onClick = {
                                    vm.setCategory(if (settings.activeCategory == cat) "" else cat)
                                },
                                label = { Text(cat) },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                        }
                    }
                }
            }

            // ---- Kategori dağılımı ----------------------------------------
            if (categories.isNotEmpty()) {
                item {
                    SectionCard("Arşivdeki dağılım") {
                        categories.take(8).forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(c.category ?: "Etiketsiz", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${c.adet}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ---- Son yakalananlar -----------------------------------------
            if (recent.isNotEmpty()) {
                item {
                    Text(
                        "Son yakalananlar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                items(recent, key = { it.id }) { q ->
                    SectionCard(modifier = Modifier.clickable { onOpenDetail(q.id) }) {
                        Text(
                            q.questionText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                q.correctText?.let { "✔ $it" } ?: "cevap bekleniyor",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (q.correctText != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatTime(q.capturedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    SectionCard {
                        Text(
                            "Henüz kayıt yok.\n\nServisi açtıktan sonra oyunu başlat ve normal " +
                                "şekilde oyna. Ekrandaki her yeni soru otomatik olarak buraya düşecek.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onPickApp,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (settings.targetPackages.isEmpty()) "Hedef uygulama seç"
                        else "İzlenen uygulamalar (${settings.targetPackages.size})"
                    )
                }
            }
        }
    }
}
