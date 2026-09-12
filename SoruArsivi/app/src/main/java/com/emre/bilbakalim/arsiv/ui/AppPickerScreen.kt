package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppPickerScreen(
    vm: ArsivViewModel,
    selected: Set<String>,
    onBack: () -> Unit
) {
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = vm.installedApps() }

    Scaffold(topBar = { ArsivTopBar("İzlenecek uygulama", onBack = onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            Text(
                "Yakalama sadece burada işaretlediğin uygulamalarda çalışır. " +
                    "Başka hiçbir uygulamada ekran okunmaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Uygulama ara (ör. Bil Bakalım)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            val list = apps
            if (list == null) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                val shown = if (filter.isBlank()) list else list.filter {
                    it.etiket.contains(filter, ignoreCase = true) ||
                        it.paket.contains(filter, ignoreCase = true)
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.paket }) { app ->
                        val checked = app.paket in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = selected.toMutableSet()
                                    if (checked) next.remove(app.paket) else next.add(app.paket)
                                    vm.setTargets(next)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = remember(app.paket) {
                                runCatching { app.ikon?.toBitmap(96, 96) }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                Spacer(Modifier.size(38.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.etiket,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    app.paket,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}
