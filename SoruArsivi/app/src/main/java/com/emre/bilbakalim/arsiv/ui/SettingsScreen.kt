package com.emre.bilbakalim.arsiv.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emre.bilbakalim.arsiv.data.Repo
import com.emre.bilbakalim.arsiv.util.Exporters

@Composable
fun SettingsScreen(
    vm: ArsivViewModel,
    onBack: () -> Unit,
    onPickApp: () -> Unit,
    onOpenDebug: () -> Unit
) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    // Kullanıcının seçtiği yedek dosyası. Dosya yöneticileri JSON'u bazen
    // "application/octet-stream" diye etiketlediği için tür süzgeci koymuyoruz;
    // yanlış dosya seçilirse zaten anlaşılır bir hata veriyoruz.
    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importFrom(context, uri) { result ->
            backupMessage = when (result) {
                is Repo.ImportResult.Ok -> buildString {
                    append(result.added).append(" yeni soru eklendi.\n")
                    append(result.merged).append(" kayıt tamamlandı (eksik cevap, şık, kategori).\n")
                    append(result.skipped).append(" kayıtta değişiklik yoktu.\n\n")
                    append("Dosyadaki toplam kayıt: ").append(result.total)
                }
                is Repo.ImportResult.Failed -> "İçe aktarılamadı: ${result.reason}"
            }
        }
    }

    Scaffold(topBar = { ArsivTopBar("Ayarlar", onBack = onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Hedef uygulama", Icons.Default.Apps) {
                    Text(
                        if (s.targetPackages.isEmpty()) "Henüz seçilmedi — yakalama çalışmaz."
                        else s.targetPackages.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
                        Text("Uygulama seç")
                    }
                }
            }

            item {
                SectionCard(
                    "Oynatma modu",
                    Icons.Default.SmartToy,
                    container = if (s.autoPlay)
                        MaterialTheme.colorScheme.tertiaryContainer else null
                ) {
                    Text(
                        if (s.autoPlay)
                            "Otomatik: uygulama oyunu kendisi oynuyor."
                        else "Manuel: oyunu sen oynuyorsun, uygulama sadece okuyor.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    SettingSwitch(
                        "Otomatik oyna",
                        "Soru ekrana gelince bir şıkka dokunulur, tur bitince " +
                            "\"Tekrar Oyna\" benzeri düğmeye basılır. Böylece oyunun " +
                            "soru havuzu başında beklemeden arşivlenir.",
                        s.autoPlay
                    ) { vm.setAutoPlay(it) }

                    if (s.autoPlay) {
                        SettingSwitch(
                            "Tur bitince yeniden başlat",
                            "Kapalıysa uygulama soruları cevaplar ama tur bitince bekler.",
                            s.autoRestart
                        ) { vm.setAutoRestart(it) }

                        SettingSwitch(
                            "Bilinen cevabı kullan",
                            "Soru arşivde varsa ve cevabı biliniyorsa doğru şıkka basılır; " +
                                "bilinmiyorsa rastgele seçilir. Şıklar her turda karıştığı " +
                                "için doğru şık sırasına göre değil metnine göre bulunur. " +
                                "Kapatırsan seçim her zaman rastgele olur.",
                            s.autoUseKnownAnswer
                        ) { vm.setAutoUseKnownAnswer(it) }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Dokunmadan önce bekleme: ${s.autoAnswerDelayMs} ms",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Bekleme, sorunun okunduğu andan değil şık kutularının " +
                                "ekrana oturduğu andan başlar; yani bu süreyi kısmak " +
                                "yarım çizilmiş bir karta dokunma riski yaratmaz. " +
                                "600-900 ms çoğu cihazda rahat çalışıyor. Daha da " +
                                "kısaltırsan oyunun dokunuşu yutma ihtimali artar; " +
                                "bot o zaman yeniden deniyor ve net bir kazanç kalmıyor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = s.autoAnswerDelayMs.toFloat(),
                            onValueChange = { vm.setAutoAnswerDelay(it.toLong()) },
                            valueRange = 200f..3000f,
                            steps = 27
                        )
                        Text(
                            "Not: otomatik mod ekrana dokunmak için erişilebilirlik " +
                                "servisinin jest iznini kullanır. Servisi bu sürümden önce " +
                                "açtıysan bir kez kapatıp yeniden açman gerekebilir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard("Okuma yöntemi") {
                    SettingSwitch(
                        "Metin okunamazsa OCR'a düş",
                        "Uygulama yazıyı normal metin olarak vermiyorsa ekran görüntüsü alınıp " +
                            "karakter tanıma yapılır.",
                        s.ocrFallback
                    ) { vm.setOcrFallback(it) }

                    SettingSwitch(
                        "Her zaman OCR ile karşılaştır",
                        "Daha doğru ama belirgin şekilde daha yavaş ve pil yiyici. " +
                            "Sadece sonuçlar bozuksa aç.",
                        s.ocrAlways
                    ) { vm.setOcrAlways(it) }

                    SettingSwitch(
                        "Doğru cevabı renkten anla",
                        "Cevap verildikten sonra yeşile dönen şıkkı doğru olarak işaretler.",
                        s.detectAnswer
                    ) { vm.setDetectAnswer(it) }

                    SettingSwitch(
                        "Ekran görüntüsünü sakla",
                        "Her soru için küçültülmüş bir görüntü tutulur; yanlış okumaları " +
                            "kontrol etmeyi kolaylaştırır.",
                        s.saveScreenshots
                    ) { vm.setSaveScreenshots(it) }

                    SettingSwitch(
                        "Dört şık tamamlanmadan kaydetme",
                        "Şıklar ekrana teker teker geliyor. Bu açıkken uygulama " +
                            "dördü de görünene kadar bekler, böylece soru üç şıkla " +
                            "eksik kaydedilmez.",
                        s.requireFourOptions
                    ) { vm.setRequireFourOptions(it) }

                    SettingSwitch(
                        "Sadece soru cümlelerini kaydet",
                        "Lobi ve skor ekranlarındaki \"Bilme Oranı\", \"Liderlik Tablosu\" " +
                            "gibi başlıkların soru sanılıp kaydedilmesini engeller. " +
                            "Gerçek sorular yakalanmıyorsa kapatıp deneyebilirsin.",
                        s.requireQuestionShape
                    ) { vm.setRequireQuestionShape(it) }

                    SettingSwitch(
                        "Kategoriyi ekrandan tanı",
                        "Ekranda bilinen bir kategori adı görünürse kayda o yazılır.",
                        s.autoDetectCategory
                    ) { vm.setAutoDetectCategory(it) }
                }
            }

            item {
                SectionCard("Kayıt eşiği") {
                    Text(
                        "Ayrıştırma güveni %${(s.minConfidence * 100).toInt()} altındaysa kaydetme.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Düşürürsen daha çok soru yakalanır ama çöp kayıt artar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = s.minConfidence,
                        onValueChange = { vm.setMinConfidence(it) },
                        valueRange = 0.2f..0.9f,
                        steps = 13
                    )
                }
            }

            item {
                SectionCard("Ekran bölgeleri") {
                    Text(
                        "Soru ve şıkların ekranın hangi bölümünde arandığını belirler. " +
                            "Arayüz farklıysa buradan ayarlayabilirsin — Teşhis ekranı " +
                            "hangi metnin nerede görüldüğünü gösterir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    RegionSlider("Soru — üst", s.questionTop) {
                        vm.setRegions(it, s.questionBottom, s.optionsTop, s.optionsBottom)
                    }
                    RegionSlider("Soru — alt", s.questionBottom) {
                        vm.setRegions(s.questionTop, it, s.optionsTop, s.optionsBottom)
                    }
                    RegionSlider("Şıklar — üst", s.optionsTop) {
                        vm.setRegions(s.questionTop, s.questionBottom, it, s.optionsBottom)
                    }
                    RegionSlider("Şıklar — alt", s.optionsBottom) {
                        vm.setRegions(s.questionTop, s.questionBottom, s.optionsTop, it)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.resetRegions() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Varsayılan")
                        }
                        OutlinedButton(onClick = onOpenDebug, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Teşhis")
                        }
                    }
                }
            }

            item {
                val fastOn by vm.fastCaptureOn.collectAsState()
                SectionCard(
                    "Hızlı yakalama",
                    container = if (fastOn) null else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        if (fastOn) "Açık — ekran karesine istendiği an bakılabiliyor."
                        else "Kapalı — saniyede yalnızca bir kare alınabiliyor.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android, erişilebilirlik ekran görüntüsünü saniyede bir kereden " +
                            "fazla almaya izin vermiyor. Şıklar ekrana teker teker geliyor " +
                            "ve cevap yarım saniyede açılıp geçiyor; bu hızda ikisi de " +
                            "kaçabiliyor. Ekran yansıtmada böyle bir sınır yok — açıkken " +
                            "karar penceresine saniyede sekiz kez bakılıyor.\n\n" +
                            "Görüntü telefondan dışarı gönderilmez. Telefonu yeniden " +
                            "başlattığında kapanır, tekrar açman gerekir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ArsivViewModel.startFastCapture(context) },
                            enabled = !fastOn,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (fastOn) "Açık" else "Aç") }
                        OutlinedButton(
                            onClick = { ArsivViewModel.stopFastCapture(context) },
                            enabled = fastOn,
                            modifier = Modifier.weight(1f)
                        ) { Text("Durdur") }
                    }
                }
            }

            item {
                SectionCard("Gizlilik") {
                    Text(
                        "• Okunan metin telefondan dışarı gönderilmez; her şey cihazdaki " +
                            "yerel veritabanında durur.\n" +
                            "• Metin tanıma çevrimdışı çalışır, internet gerekmez.\n" +
                            "• Ekran yalnızca yukarıda işaretlediğin uygulamalar önplandayken okunur.\n" +
                            "• Manuel modda uygulama hedef uygulamaya dokunmaz: tıklama, " +
                            "kaydırma veya herhangi bir jest göndermez, sadece görüneni okur.\n" +
                            "• Otomatik modda ise ekrana dokunur: şıklardan birine ve tur " +
                            "sonundaki yeniden başlatma düğmesine. Başka hiçbir yere " +
                            "dokunmaz, tanımadığı düğmeye basmaz.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                SectionCard("Yedekleme", Icons.Default.Backup) {
                    Text(
                        "Arşivi JSON olarak dışa aktarıp saklayabilir, sonra buradan " +
                            "geri yükleyebilirsin. Uygulamayı silip yeniden kurman " +
                            "gerektiğinde sorularını böyle taşırsın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "İçe aktarma hiçbir şeyi silmez: aynı soru arşivde zaten varsa " +
                            "yalnızca eksikleri tamamlanır. Aynı dosyayı iki kez almanın " +
                            "zararı yok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                vm.export(context, Exporters.Format.JSON) { file ->
                                    if (file == null) {
                                        backupMessage = "Dışa aktarılacak kayıt yok."
                                    } else {
                                        Exporters.share(context, file, Exporters.Format.JSON)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Dışa aktar") }
                        Button(
                            onClick = { pickBackup.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("İçe aktar") }
                    }
                }
            }

            item {
                SectionCard("Tehlikeli bölge") {
                    Button(
                        onClick = { confirmWipe = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tüm arşivi sil")
                    }
                }
            }
        }
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("Yedekleme") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { backupMessage = null }) { Text("Tamam") }
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Tüm sorular silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz. Önce dışa aktarmak isteyebilirsin.") },
            confirmButton = {
                TextButton(onClick = { confirmWipe = false; vm.deleteAll() }) { Text("Hepsini sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RegionSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("%${(value * 100).toInt()}", style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
