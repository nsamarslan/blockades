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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ListScreen(
    vm: ArsivViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }

    val query by vm.query.collectAsState()
    val filterCat by vm.filterCategory.collectAsState()
    val onlyUnanswered by vm.onlyUnanswered.collectAsState()
    val results by vm.results.collectAsState()
    val categories by vm.categories.collectAsState()

    // Dışa aktarım ekran dönse de sürüyor; penceresi de açık kalsın.
    val disaAktarimSuruyor by vm.disaAktarim.collectAsState()
    var disaAktar by remember { mutableStateOf(false) }
    if (disaAktar || disaAktarimSuruyor != null) {
        // Listede bir kategori süzülmüşse pencere onunla açılıyor.
        DisaAktarPenceresi(vm, DisaAktarimSecimi.PDF, kategori = filterCat) { disaAktar = false }
    }

    Scaffold(
        topBar = {
            ArsivTopBar("Arşiv (${results.size})", onBack = onBack) {
                IconButton(onClick = { disaAktar = true }) {
                    Icon(Icons.Default.Share, contentDescription = "Dışa aktar")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Soru veya şık ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle")
                        }
                    }
                }
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = onlyUnanswered,
                        onClick = { vm.onlyUnanswered.value = !onlyUnanswered },
                        label = { Text("Cevabı eksik") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterCat == null,
                        onClick = { vm.filterCategory.value = null },
                        label = { Text("Tümü") }
                    )
                }
                items(categories) { c ->
                    val name = c.category ?: return@items
                    FilterChip(
                        selected = filterCat == name,
                        onClick = { vm.filterCategory.value = if (filterCat == name) null else name },
                        label = { Text("$name (${c.adet})") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (results.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (query.isBlank()) "Arşiv boş." else "Eşleşen soru yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { it.id }) { q ->
                        SectionCard(modifier = Modifier.clickable { onOpenDetail(q.id) }) {
                            Text(
                                q.questionText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            q.options.forEachIndexed { i, opt ->
                                val correct = q.correctIndex == i
                                Text(
                                    "${optionLetter(i)}) $opt",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (correct) FontWeight.Bold else FontWeight.Normal,
                                    color = if (correct) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (q.seenCount > 1 || q.answeredCount > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    q.statsLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    listOfNotNull(
                                        q.category,
                                        if (q.confidence < 0.6f) "düşük güven" else null
                                    ).joinToString(" · ").ifBlank { "etiketsiz" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatTime(q.capturedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
