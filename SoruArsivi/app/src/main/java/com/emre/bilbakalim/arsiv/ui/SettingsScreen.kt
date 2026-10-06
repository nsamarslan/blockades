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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emre.bilbakalim.arsiv.data.AnahtarKotasi
import com.emre.bilbakalim.arsiv.util.OtomatikYedek
import com.emre.bilbakalim.arsiv.data.Repo
import com.emre.bilbakalim.arsiv.capture.YapayZeka
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.emre.bilbakalim.arsiv.data.YapayZekaIstatistik

@Composable
fun SettingsScreen(
    vm: ArsivViewModel,
    onBack: () -> Unit,
    onPickApp: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenEkranAyarla: () -> Unit,
    onOpenYzKontrol: () -> Unit,
    onOpenBenzer: () -> Unit
) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    // Dışa aktarım ekran dönse de sürüyor; penceresi de açık kalsın.
    val disaAktarimSuruyor by vm.disaAktarim.collectAsState()
    var disaAktar by remember { mutableStateOf(false) }
    if (disaAktar || disaAktarimSuruyor != null) {
        DisaAktarPenceresi(vm, DisaAktarimSecimi.JSON) { disaAktar = false }
    }

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
                            "Can bitince doldur",
                            "\"Can Kalmadı\" penceresi çıkınca \"Doldur\"a basar " +
                                "(4000 altın). Kapalıysa pencerede bekler.",
                            s.autoRefillLives
                        ) { vm.setAutoRefillLives(it) }

                        SettingSwitch(
                            "Bilinen cevabı kullan",
                            "Soru arşivde varsa ve cevabı biliniyorsa doğru şıkka basılır; " +
                                "bilinmiyorsa rastgele seçilir. Şıklar her turda karıştığı " +
                                "için doğru şık sırasına göre değil metnine göre bulunur. " +
                                "Kapatırsan seçim her zaman rastgele olur.",
                            s.autoUseKnownAnswer
                        ) { vm.setAutoUseKnownAnswer(it) }

                        SettingSwitch(
                            "Cevap bilinmiyorsa rastgele bas",
                            "Açıkken bilmediği soruda da bir şıkka dokunur; oyun " +
                                "akmaya devam eder ve doğru cevap oyunun tepkisinden " +
                                "öğrenilir. Kapatırsan o soruda hiç dokunmaz, kararı " +
                                "sana bırakır — sen cevapladığında doğrusu yine arşive " +
                                "yazılır ama yanlış bir tahminle tur harcanmaz.",
                            s.autoRandomWhenUnknown
                        ) { vm.setAutoRandomWhenUnknown(it) }


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

                    // Otomatik blokunun dışında: "oyun uzakta" modunda da
                    // çalışıyor (yayındaki bilinmeyen soruya mor ok).
                    SettingSwitch(
                        "Bilinmeyen soruyu yapay zekâya sor",
                        "Cevabı arşivde olmayan soruda rastgele basmak yerine soru " +
                            "ve şıklar yapay zekâya sorulur, onun seçtiği şıkka basılır. " +
                            "İki anahtar da girildiyse sorular Groq ile Gemini arasında " +
                            "dönüşümlü dağıtılır; böylece dakikalık ve günlük ücretsiz " +
                            "kotalar eşit erir. Birinin kotası dolarsa (token hakkı " +
                            "kalmazsa) ya da cevap veremezse soru ötekine sorulur. İkisi de cevap " +
                            "veremezse otomatik modun ayarına göre rastgele basılır ya da " +
                            "karar sana bırakılır. Doğru cevap yine oyunun tepkisinden " +
                            "öğrenilip arşive yazılır.\n" +
                            "Oyun uzakta modunda da çalışır: yayındaki bilinmeyen sorunun " +
                            "yapay zekânın seçtiği şıkkına mor ok konur (arşivden bilinen " +
                            "cevap yeşil oktur).",
                        s.aiWhenUnknown
                    ) { vm.setAiWhenUnknown(it) }

                    if (s.aiWhenUnknown) {
                        YapayZekaGuveni(vm, s.aiGuvenEsigi, s.aiEminDegilseBirak)
                        YapayZekaBasarisi(vm)
                        OutlinedButton(onClick = onOpenYzKontrol, modifier = Modifier.fillMaxWidth()) {
                            Text("Arşivi yapay zekâyla kontrol et")
                        }
                        OutlinedButton(onClick = onOpenBenzer, modifier = Modifier.fillMaxWidth()) {
                            Text("Benzer kayıtları birleştir")
                        }
                        YapayZekaAyarlari(vm, s.groqKeys, s.geminiKeys)
                    }

                    // Bilerek otomatik blokunun dışında: ses manuel modda da
                    // çalışıyor, oyunu kendin oynarken de "bu soru bizde yok"
                    // bilgisini veriyor.
                    SettingSwitch(
                        "Cevabı bilinmeyen soruda uyarı sesi",
                        "Telefonun bildirim sesi medya ses seviyesinden çalar (oyunu " +
                            "duyuyorsan bunu da duyarsın; oyun modları ve Rahatsız Etmeyin " +
                            "susturamaz). Manuel modda da çalışır; yapay zekâya sorulan " +
                            "sorularda da çalar. İki ayrı uyarı var:\n" +
                            "• Tek ötüş — soru okundu ama cevabı arşivde yok " +
                            "(ya da kayıttaki cevap ekrandaki şıklara uymuyor).\n" +
                            "• Çift ötüş — ekranda soru var ama şıklar okunamıyor. " +
                            "Bu durumda soru arşivde kayıtlı bile olabilir; sorun " +
                            "arşivde değil okumada.",
                        s.unknownChime
                    ) { vm.setUnknownChime(it) }
                }
            }

            item { KesintisizCalisma() }

            item {
                SectionCard("Okuma yöntemi") {
                    SettingSwitch(
                        "Şık kutularını ekrandan ölç",
                        "Şıkların kaç tane olduğunu ve nerede durduğunu yazıdan değil, " +
                            "ekrandaki parlak hapların kendisinden bulur; her hap ayrı " +
                            "okunur. Şıkları sayı olan sorular (\"1\", \"3\", \"4\") ancak " +
                            "böyle okunabiliyor. Kapatırsan eski yönteme dönülür.",
                        s.findOptionBoxes
                    ) { vm.setFindOptionBoxes(it) }

                    SettingSwitch(
                        "Okunamayan kareyi teşhis için sakla",
                        "Şıklar okunamadığında o anın görüntüsü ve ham OCR dökümü " +
                            "uygulamanın klasörüne yazılır (en son 20 kare). Arıza " +
                            "tekrarlarsa sebebi tahmin etmek yerine bakılabiliyor.",
                        s.saveFailedFrames
                    ) { vm.setSaveFailedFrames(it) }

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
                            "Başka bir telefonda ya da tablette sorular okunmuyorsa " +
                            "\"Ekranı ayarla\" ile oyunun bir karesi üstünde soru ve şık " +
                            "bölgelerini parmağınla çiz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenEkranAyarla, modifier = Modifier.fillMaxWidth()) {
                        Text("Ekranı ayarla")
                    }
                    Text(
                        if (s.soruBolgesi != null && s.sikBolgesi != null) "Bölgeler elle seçildi."
                        else "Bölgeler ekrandan kendiliğinden bulunuyor.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
                            "gerektiğinde sorularını böyle taşırsın. Dışa aktar'da " +
                            "kategori seçip soruları ekran görüntüsüyle PDF, Word ya da " +
                            "fotoğraf (ZIP) olarak da alabilirsin; doğru şık işaretli.",
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
                            onClick = { disaAktar = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Dışa aktar") }
                        Button(
                            onClick = { pickBackup.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("İçe aktar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    val sonYedek by vm.sonYedek.collectAsState()
                    SettingSwitch(
                        "Günlük otomatik yedek",
                        "Arşiv günde bir kez İndirilenler/SoruArsivi/Yedekler klasörüne " +
                            "JSON olarak yedeklenir; son ${OtomatikYedek.SAKLANAN} gün tutulur. " +
                            "Toplu bir düzeltme ya da içe aktarma arşivi bozarsa o günün " +
                            "dosyasını İçe aktar ile geri yükleyebilirsin." +
                            (sonYedek?.let { "\nSon yedek: ${formatTime(it.zaman)} · ${it.adet} soru · ${it.yer}" }
                                ?: "\nHenüz yedek alınmadı."),
                        s.otomatikYedek
                    ) { vm.setOtomatikYedek(it) }
                    OutlinedButton(
                        onClick = { vm.simdiYedekle { backupMessage = it } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Şimdi yedekle") }
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

/**
 * Pil kısıtlaması: telefon uygulamayı arka planda uyuttuğunda ekran
 * yakalama kapanıyor ve cevaplar kaydedilmiyordu. Durum ekrana her
 * dönüldüğünde yeniden okunuyor (ayardan geri gelince güncellensin).
 */
@Composable
private fun KesintisizCalisma() {
    val context = LocalContext.current
    var serbest by remember { mutableStateOf(pilSerbest(context)) }
    LifecycleResumeEffect(Unit) {
        serbest = pilSerbest(context)
        onPauseOrDispose { }
    }
    SectionCard("Kesintisiz çalışma") {
        Text(
            if (serbest) "Pil kısıtlaması kaldırılmış ✓"
            else "Pil kısıtlaması açık: telefon uygulamayı arka planda durdurabilir.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            "Hızlı yakalama kapanırsa sorular yine kaydedilir ama cevaplar " +
                "kaçar. Kapanmasının üç sebebi var: telefonun pil tasarrufu " +
                "uygulamayı durdurur, ekran kilitlenir ya da bildirimden " +
                "\"paylaşımı durdur\" denir. Hızlı yakalama açıkken ekran artık " +
                "kendiliğinden kapanmıyor; yine de kapanırsa sesli bir bildirim " +
                "gelir, dokunup yeniden açabilirsin. Sebebi teşhis günlüğünde " +
                "\"HIZLI YAKALAMA KAPANDI\" satırında yazar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!serbest) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pil kısıtlamasını kaldır") }
        }
    }
}

private fun pilSerbest(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

/**
 * Yapay zekânın gerçek başarısı: oyunun gösterdiği doğru cevapla
 * karşılaştırılan tahminler, sağlayıcıya, güvene ve kategoriye göre.
 */
@Composable
private fun YapayZekaBasarisi(vm: ArsivViewModel) {
    val veri by vm.aiIstatistik.collectAsState()
    var acik by remember { mutableStateOf(false) }
    var sifirlaSor by remember { mutableStateOf(false) }
    if (sifirlaSor) {
        AlertDialog(
            onDismissRequest = { sifirlaSor = false },
            title = { Text("Başarı istatistiği silinsin mi?") },
            text = { Text("Sayaçlar sıfırlanır; arşive dokunulmaz.") },
            confirmButton = { TextButton(onClick = { vm.aiIstatistikSifirla(); sifirlaSor = false }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { sifirlaSor = false }) { Text("Vazgeç") } }
        )
    }
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 8.dp)) {
        Text("Yapay zekânın başarısı", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (veri.isEmpty()) {
            Text(
                "Henüz veri yok. Yapay zekâya sorulan bir sorunun doğru cevabı " +
                    "oyunda açıldıkça burada sayılır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        fun satir(s: YapayZekaIstatistik.Sayac) = "%${s.yuzde ?: 0} (${s.dogru}/${s.toplam})"
        veri.entries.sortedByDescending { it.value.toplam.toplam }.forEach { (ad, s) ->
            Text("$ad: ${satir(s.toplam)} doğru", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = { acik = !acik }) { Text(if (acik) "Ayrıntıyı gizle" else "Güvene ve kategoriye göre") }
        if (acik) {
            veri.entries.sortedByDescending { it.value.toplam.toplam }.forEach { (ad, s) ->
                Text(ad, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                YapayZekaIstatistik.GUVEN_ARALIKLARI.forEach { g ->
                    s.guven[g]?.let {
                        Text("  güven $g: ${satir(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                s.kategori.entries.sortedByDescending { it.value.toplam }.forEach { (k, v) ->
                    Text("  $k: ${satir(v)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Güven aralıkları eşiği seçmek için: düşük güvenli tahminlerin " +
                    "başarısı düşükse eşiği o aralığın üstüne çek.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { sifirlaSor = true }) { Text("Sıfırla") }
        }
    }
}

/** Yapay zekâ emin değilse ne yapılsın. */
@Composable
private fun YapayZekaGuveni(vm: ArsivViewModel, esik: Int, birak: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        Text("Emin sayılması için güven: %$esik", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Yapay zekâ cevabıyla birlikte ne kadar emin olduğunu da söylüyor " +
                "(0-100). Bildiği sorularda 90-100, tahminlerde daha düşük.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = esik.toFloat(),
            onValueChange = { vm.setAiGuvenEsigi((it / 5).toInt() * 5) },
            valueRange = 30f..95f,
            steps = 12
        )
        SettingSwitch(
            "Emin değilse karar bende",
            if (birak) "Açık: güven eşiğin altındaysa otomatik mod dokunmaz, soruyu " +
                "sen cevaplarsın. Oyun uzakta modunda tahmin soluk okla gösterilir."
            else "Kapalı: emin olmasa da yapay zekânın seçtiği şıkka basılır. " +
                "Oyun uzakta modunda tahmin soluk okla gösterilir.",
            birak
        ) { vm.setAiEminDegilseBirak(it) }
    }
}

/** Groq ve Gemini anahtarları (her birinden birden çok) ve "dene" düğmesi. */
@Composable
private fun YapayZekaAyarlari(
    vm: ArsivViewModel,
    groqKeys: List<String>,
    geminiKeys: List<String>
) {
    var goster by remember { mutableStateOf(false) }
    var deneniyor by remember { mutableStateOf(false) }
    var sonuc by remember { mutableStateOf<String?>(null) }
    val gizle = if (goster) VisualTransformation.None else PasswordVisualTransformation()

    Column(
        Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnahtarAlanlari("Groq", "gsk_…", groqKeys, gizle) { vm.setGroqKeys(it) }
        AnahtarAlanlari("Gemini", null, geminiKeys, gizle) { vm.setGeminiKeys(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = goster, onCheckedChange = { goster = it })
            Text("Anahtarları göster", style = MaterialTheme.typography.bodySmall)
        }
        AnahtarDurumlari(groqKeys, geminiKeys)
        Text(
            "Sorular anahtarlar arasında sırayla dağıtılır: Groq 1, Gemini 1, " +
                "Groq 2, Gemini 2… Biri cevap veremezse ya da kotası dolarsa " +
                "sıradakine sorulur. Ücretsiz kota hesap (Gemini'de proje) başına " +
                "tutulduğu için kotayı artırmak istiyorsan anahtarları ayrı " +
                "hesaplardan al; aynı hesabın anahtarları aynı kotayı paylaşır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = {
                deneniyor = true
                sonuc = null
                vm.yapayZekaDene { sonuc = it; deneniyor = false }
            },
            enabled = !deneniyor && (groqKeys + geminiKeys).any { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (deneniyor) "Deneniyor…" else "Anahtarları dene")
        }
        sonuc?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Anahtarlar yalnızca bu telefonda saklanır ve yalnızca soru ile " +
                "şıkları göndermek için kullanılır. Ücretsiz anahtar: " +
                "console.groq.com ve aistudio.google.com.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Her anahtarın bugünkü kullanımı ve şu anki durumu (kota bekleniyor mu,
 * reddedildi mi). Kota beklemesi geri saydığı için birkaç saniyede bir
 * yenileniyor.
 */
@Composable
private fun AnahtarDurumlari(groqKeys: List<String>, geminiKeys: List<String>) {
    val anahtarlar = YapayZeka.anahtarSirasi(groqKeys, geminiKeys)
    if (anahtarlar.isEmpty()) return
    val durumlar by AnahtarKotasi.durumlar.collectAsState()
    var tik by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); tik++ }
    }
    // Kota cezası bellekte duruyor (akış değil); her tikte yeniden okunuyor.
    val cezaHaritasi = remember(tik, anahtarlar) {
        anahtarlar.associateWith { a -> YapayZeka.modeller(a.saglayici).map { YapayZeka.kalanCeza(a, it) } }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("Anahtarların durumu (bugün)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        anahtarlar.forEach { a ->
            val d = AnahtarKotasi.bugunku(durumlar[AnahtarKotasi.ozet(a.saglayici.ad, a.deger)])
            val cezalar = cezaHaritasi[a].orEmpty().ifEmpty { listOf(0L) }
            val durum = when {
                cezalar.all { it > 0 } -> "⏳ kota dolu, ~${cezalar.min() / 60_000 + 1} dk sonra açılır"
                cezalar.first() > 0 -> "⚠ asıl modelin kotası dolu (~${cezalar.first() / 60_000 + 1} dk), yedek model kullanılıyor"
                d.istek == 0 -> "henüz kullanılmadı"
                else -> "✓ hazır"
            }
            val kalan = if (d.kalanIstek != null && d.istekSiniri != null)
                " · kalan ${d.kalanIstek}/${d.istekSiniri} istek" else ""
            Text(
                "${a.ad}: ${d.istek} istek" +
                    (if (d.kotaHatasi > 0) " (${d.kotaHatasi} kez kotaya takıldı)" else "") +
                    "$kalan · $durum",
                style = MaterialTheme.typography.bodySmall
            )
            if (d.sonHata != null && d.sonHataAt > d.sonBasariAt) {
                Text(
                    "   son hata ${formatTime(d.sonHataAt)}: ${d.sonHata.take(90)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            "Groq kalan günlük isteği kendisi bildiriyor; Gemini bildirmediği için " +
                "yalnızca sayılıyor. Kota oyun, oyun uzakta ve toplu kontrol arasında ortak.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bir sağlayıcının anahtar alanları. Doldurdukça altına yeni boş alan
 * açılıyor ([EN_COK_ANAHTAR]'a kadar).
 */
@Composable
private fun AnahtarAlanlari(
    ad: String,
    ipucu: String?,
    kayitli: List<String>,
    gizle: VisualTransformation,
    onChange: (List<String>) -> Unit
) {
    var liste by remember { mutableStateOf(kayitli) }
    val gorunen = if (liste.size < EN_COK_ANAHTAR && liste.lastOrNull()?.isNotBlank() != false) {
        liste + ""
    } else liste
    gorunen.forEachIndexed { i, deger ->
        OutlinedTextField(
            value = deger,
            onValueChange = { yeni ->
                val l = gorunen.toMutableList()
                l[i] = yeni
                liste = l.dropLastWhile { it.isBlank() }
                onChange(liste)
            },
            label = { Text("$ad anahtarı ${i + 1}" + (ipucu?.let { " ($it)" } ?: "")) },
            singleLine = true,
            visualTransformation = gizle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val EN_COK_ANAHTAR = 6

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
