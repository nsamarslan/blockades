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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.capture.CaptureAccessibilityService

/**
 * Bu oturumda bildiremediğimiz sorular.
 *
 * Arşiv ekranı "doğru cevap neydi" sorusunu zaten yanıtlıyor; burada
 * yanıtlanan başka bir soru var: **biz neye bastık.** Bot rastgele mi
 * seçti, arşivdeki cevap yanlış mıydı, yoksa süre mi doldu — ancak ikisi
 * yan yana görülünce anlaşılıyor.
 *
 * Liste bellekte duruyor ve uygulama kapanınca gidiyor: bu bir arşiv değil,
 * "az önce ne oldu" defteri. Şişmesin diye [CaptureAccessibilityService.MISS_LIMIT]
 * satırda tutuluyor, üstüne bir de Temizle var.
 */
@Composable
fun MissesScreen(
    vm: ArsivViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val misses by vm.misses.collectAsState()

    Scaffold(topBar = { ArsivTopBar("Hatalar", onBack = onBack) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Bu oturum (${misses.size})") {
                    Text(
                        "Bildiremediğimiz sorular: neye bastığımız ve doğrusunun ne " +
                            "olduğu. Uygulama kapanınca liste sıfırlanır; sorunun " +
                            "kendisi arşivde kalır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { vm.clearMisses() },
                        enabled = misses.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Temizle") }
                }
            }

            if (misses.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            "Henüz hata yok. Cevabı kaçan ya da yanlış bilinen bir " +
                                "soru çıkınca burada listelenir.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(misses, key = { it.seq }) { m -> MissCard(m, onOpenDetail) }
        }
    }
}

@Composable
private fun MissCard(
    m: CaptureAccessibilityService.Miss,
    onOpenDetail: (Long) -> Unit
) {
    SectionCard(modifier = Modifier.clickable { onOpenDetail(m.questionId) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (m.timedOut) "süre doldu" else if (m.byAuto) "otomatik" else "elle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTime(m.at),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(m.question, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        AnswerRow(
            "Bastığımız",
            m.chosen?.let { text -> m.chosenLabel?.let { "$it) $text" } ?: text }
                ?: if (m.timedOut) "— (basılmadı)" else "— (okunamadı)",
            MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        AnswerRow(
            "Doğrusu",
            "${m.correctLabel}) ${m.correct}",
            MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AnswerRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
