package com.emre.bilbakalim.arsiv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.emre.bilbakalim.arsiv.data.KategoriListesi

/**
 * Seçilebilen, basılı tutulunca adı değiştirilebilen kategori çipi.
 *
 * FilterChip uzun basmayı desteklemediği için onun görünüşünde küçük bir
 * eşdeğeri.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KategoriCipi(
    ad: String,
    secili: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val renk = MaterialTheme.colorScheme
    val sekil = RoundedCornerShape(8.dp)
    Surface(
        shape = sekil,
        color = if (secili) renk.secondaryContainer else Color.Transparent,
        contentColor = if (secili) renk.onSecondaryContainer else renk.onSurfaceVariant,
        border = if (secili) null else BorderStroke(1.dp, renk.outline),
        modifier = Modifier
            .height(32.dp)
            .clip(sekil)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Adını değiştir"
            )
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (secili) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(ad, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Yeni kategori" penceresi. [onEkle] sadeleşmiş adla çağrılır. */
@Composable
fun KategoriEkleDialog(
    liste: KategoriListesi,
    onDismiss: () -> Unit,
    onEkle: (String) -> Unit
) {
    var ad by remember { mutableStateOf("") }
    val temiz = KategoriListesi.sadelestir(ad)
    val varOlan = liste.bul(temiz)
    val olur = temiz.isNotEmpty() && varOlan == null
    val odak = remember { FocusRequester() }
    LaunchedEffect(Unit) { odak.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni kategori") },
        text = {
            Column {
                OutlinedTextField(
                    value = ad,
                    onValueChange = { ad = it },
                    label = { Text("Kategori adı") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (olur) onEkle(temiz) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(odak)
                )
                if (varOlan != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "«$varOlan» zaten listede.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEkle(temiz) }, enabled = olur) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

/**
 * Kategorinin adını değiştirme penceresi.
 *
 * [adet] arşivde bu kategoride kaç soru olduğu; hepsinin de yeni adı
 * alacağı açıkça yazılıyor. Yeni ad listede başka bir kategoriyse iki
 * kategorinin birleşeceği önceden söyleniyor.
 */
@Composable
fun KategoriAdiDialog(
    eski: String,
    adet: Int,
    liste: KategoriListesi,
    onDismiss: () -> Unit,
    onKaydet: (String) -> Unit
) {
    var alan by remember { mutableStateOf(TextFieldValue(eski, TextRange(0, eski.length))) }
    val temiz = KategoriListesi.sadelestir(alan.text)
    val birlesecek = liste.bul(temiz)
        ?.takeIf { KategoriListesi.anahtar(it) != KategoriListesi.anahtar(eski) }
    val olur = temiz.isNotEmpty() && temiz != KategoriListesi.sadelestir(eski)
    val odak = remember { FocusRequester() }
    LaunchedEffect(Unit) { odak.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorinin adı") },
        text = {
            Column {
                OutlinedTextField(
                    value = alan,
                    onValueChange = { alan = it },
                    label = { Text("Yeni ad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (olur) onKaydet(temiz) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(odak)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (adet > 0) "«$eski» kategorisindeki $adet soru da yeni adı alacak."
                    else "Arşivde bu kategoride henüz soru yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (birlesecek != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "«$birlesecek» zaten var: iki kategori birleşecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onKaydet(temiz) }, enabled = olur) {
                Text(if (birlesecek != null) "Birleştir" else "Değiştir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
