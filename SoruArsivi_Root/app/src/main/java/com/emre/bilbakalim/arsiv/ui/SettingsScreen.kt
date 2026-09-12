package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@Composable
fun SettingsScreen(viewModel: ArsivViewModel) {
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val autoRestart by viewModel.autoRestartGame.collectAsState()

    var delayInputText by remember(clickDelayMs) { mutableStateOf(clickDelayMs.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ÇALIŞMA MODU", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isManual = playMode == PlayMode.MANUAL
                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.MANUAL) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isManual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Manuel", color = if (isManual) Color.White else MaterialTheme.colorScheme.onSurface)
                    }

                    val isAuto = playMode == PlayMode.AUTO
                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAuto) Color(0xFF10B981) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Otomatik", color = if (isAuto) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Text("TIKLAMA GECİKMESİ (Milisaniye)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = delayInputText,
                    onValueChange = { input ->
                        delayInputText = input
                        val parsed = input.toLongOrNull()
                        if (parsed != null && parsed >= 200) {
                            viewModel.setClickDelayMs(parsed)
                        }
                    },
                    label = { Text("Gecikme (ms)") },
                    suffix = { Text("ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500L to "Hızlı (0.5s)", 1200L to "Normal (1.2s)", 2500L to "Doğal (2.5s)").forEach { (ms, label) ->
                        FilterChip(
                            selected = clickDelayMs == ms,
                            onClick = {
                                delayInputText = ms.toString()
                                viewModel.setClickDelayMs(ms)
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text("OYUN SONU & TEKRAR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Otomatik 'Yeni Oyun' Başlat", fontWeight = FontWeight.SemiBold)
                    Text("Oyun sonu ekranında 'Yeni Oyun' butonuna tıklar.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoRestart, onCheckedChange = { viewModel.setAutoRestartGame(it) })
            }
        }
    }
}
