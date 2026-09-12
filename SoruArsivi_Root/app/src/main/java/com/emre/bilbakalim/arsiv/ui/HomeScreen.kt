package com.emre.bilbakalim.arsiv.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emre.bilbakalim.arsiv.data.PlayMode

@Composable
fun HomeScreen(viewModel: ArsivViewModel) {
    val context = LocalContext.current
    val playMode by viewModel.playMode.collectAsState()
    val clickDelayMs by viewModel.clickDelayMs.collectAsState()
    val totalCount by viewModel.totalQuestionsCount.collectAsState()
    val autoRestart by viewModel.autoRestartGame.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Durum Kartı
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (playMode == PlayMode.AUTO) Color(0xFF064E3B) else Color(0xFF1E293B)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (playMode == PlayMode.AUTO) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    shape = CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (playMode == PlayMode.AUTO) "OTOMATİK BOT AKTİF" else "MANUEL MOD AKTİF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (playMode == PlayMode.AUTO) Color(0xFF6EE7B7) else Color(0xFFCBD5E1)
                        )
                    }

                    Text(
                        text = "$totalCount Soru Kayıtlı",
                        fontSize = 12.sp,
                        color = if (playMode == PlayMode.AUTO) Color(0xFFA7F3D0) else Color(0xFF94A3B8)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.setPlayMode(PlayMode.MANUAL) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (playMode == PlayMode.MANUAL) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manuel", color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.setPlayMode(PlayMode.AUTO) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playMode == PlayMode.AUTO) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Otomatik Bot", color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (playMode == PlayMode.AUTO)
                        "⚡ Tıklama Hızı: ${clickDelayMs}ms | Bilinen sorular doğru yanıtlanacak, yeni sorular rastgele denenip öğrenilecek, oyun bitince 'Yeni Oyun'a basılacak."
                    else
                        "🖐️ Manuel Mod: Ekrana tıklanmaz. Siz oynarken doğru cevaplar veritabanına kaydedilir.",
                    fontSize = 11.sp,
                    color = if (playMode == PlayMode.AUTO) Color(0xFFD1FAE5) else Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }
        }

        // Erişilebilirlik Servisi Butonu
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Accessibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Erişilebilirlik Servisini Aç / Kontrol Et")
        }

        // TRT Bil Bakalım Başlat Butonu
        OutlinedButton(
            onClick = {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.trt.bilbakalim")
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("TRT Bil Bakalım Oyununu Başlat")
        }
    }
}
