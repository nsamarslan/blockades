package com.emre.bilbakalim.arsiv.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.data.Prefs
import java.io.File

@Composable
fun DetailScreen(vm: ArsivViewModel, id: Long, onBack: () -> Unit) {

    val question by vm.observeQuestion(id).collectAsState(initial = null)

    var qText by remember { mutableStateOf("") }
    var opts by remember { mutableStateOf(listOf("", "", "", "")) }
    var correct by remember { mutableStateOf<Int?>(null) }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Kayıt geldiğinde formu bir kez doldur (kullanıcı yazarken üzerine yazmasın).
    LaunchedEffect(question?.id) {
        val q = question ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        qText = q.questionText
        opts = listOf(q.optionA ?: "", q.optionB ?: "", q.optionC ?: "", q.optionD ?: "")
        correct = q.correctIndex
        category = q.category ?: ""
        note = q.note ?: ""
        loaded = true
    }

    Scaffold(
        topBar = {
            ArsivTopBar("Soru #$id", onBack = onBack) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
        }
    ) { pad ->
        val q = question
        if (q == null) {
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
                Text("Kayıt bulunamadı.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Soru") {
                    OutlinedTextField(
                        value = qText,
                        onValueChange = { qText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }

            item {
                SectionCard("Şıklar") {
                    Text(
                        "Doğru cevabı işaretlemek için harfe dokun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    opts.forEachIndexed { i, value ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = correct == i,
                                onClick = { correct = if (correct == i) null else i },
                                label = { Text(optionLetter(i).toString()) }
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { nv ->
                                    opts = opts.toMutableList().also { it[i] = nv }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = false
                            )
                        }
                    }
                }
            }

            item {
                SectionCard("Kategori ve not") {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Prefs.BILINEN_KATEGORILER.take(4).forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Not (isteğe bağlı)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        vm.updateQuestion(
                            q.copy(
                                questionText = qText.trim(),
                                optionA = opts.getOrNull(0)?.trim()?.ifBlank { null },
                                optionB = opts.getOrNull(1)?.trim()?.ifBlank { null },
                                optionC = opts.getOrNull(2)?.trim()?.ifBlank { null },
                                optionD = opts.getOrNull(3)?.trim()?.ifBlank { null },
                                correctIndex = correct,
                                answerSource = if (correct != q.correctIndex) "elle" else q.answerSource,
                                category = category.trim().ifBlank { null },
                                note = note.trim().ifBlank { null }
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kaydet")
                }
            }

            item {
                SectionCard("Bu soruyla geçmişin") {
                    InfoRow("Kaç kez çıktı", "${q.seenCount} kez")
                    InfoRow(
                        "Cevapladığın",
                        if (q.answeredCount > 0) "${q.answeredCount} kez" else "henüz yok"
                    )
                    InfoRow(
                        "Doğru bilme",
                        q.successRate?.let { "%$it  (${q.correctCount}/${q.answeredCount})" } ?: "—"
                    )
                }
            }

            item {
                SectionCard("Kayıt bilgisi") {
                    InfoRow("Yakalama", q.source)
                    InfoRow("Güven", "%${(q.confidence * 100).toInt()}")
                    InfoRow("Cevap kaynağı", q.answerSource ?: "—")
                    InfoRow("Tarih", formatTime(q.capturedAt))
                }
            }

            val path = q.screenshotPath
            if (path != null) {
                item {
                    val bmp = remember(path) {
                        runCatching {
                            if (File(path).exists()) BitmapFactory.decodeFile(path) else null
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        SectionCard("Ekran görüntüsü") {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Yakalanan ekran",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Bu soru silinsin mi?") },
            text = { Text("Kayıt kalıcı olarak kaldırılacak.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteQuestion(id)
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
