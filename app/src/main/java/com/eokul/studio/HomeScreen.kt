package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(studio: StudioViewModel) {
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri)
    }
    var newClassOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var alsoFilesClass by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "E-Okul Foto Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Öğrenci fotoğraf yönetimi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                    }
                    var toolsMenu by remember { mutableStateOf(false) }
                    Box {
                        MoreBtn(onClick = { toolsMenu = true }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        DropdownMenu(expanded = toolsMenu, onDismissRequest = { toolsMenu = false }) {
                            DropdownMenuItem(text = { Text("📦 Tüm sınıflar tek ZIP (e-Okul)") }, enabled = studio.classes.isNotEmpty(), onClick = {
                                toolsMenu = false
                                studio.exportAllEokulZip()
                            })
                            DropdownMenuItem(text = { Text("🩺 Veri denetimi") }, enabled = studio.classes.isNotEmpty(), onClick = {
                                toolsMenu = false
                                studio.auditData()
                            })
                            DropdownMenuItem(text = { Text("✂ Toplu kırpma (hazır fotoğraflar)") }, onClick = {
                                toolsMenu = false
                                studio.navigate(Screen.Batch)
                            })
                        }
                    }
                    SettingsBtn(onClick = { studio.navigate(Screen.Settings) }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            if (studio.settings.rootUri == null || !studio.hasRootAccess()) {
                Card(
                    Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (studio.settings.rootUri == null) "Kayıt klasörü seçilmedi" else "Kayıt klasörü izni kaybolmuş",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Fotoğraflar seçeceğiniz klasörün altında sınıf klasörlerine kaydedilir.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.size(6.dp))
                        Button(onClick = { rootPicker.launch(null) }) { Text("📂 Klasör seç") }
                    }
                }
            }

            if (studio.classes.isNotEmpty()) SummaryCard(studio)

            if (studio.classes.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Henüz sınıf yok", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Sınıf ekleyin, ardından e-Okul veya Excel’den kopyaladığınız numara + ad soyad listesini yapıştırın.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { newClassOpen = true }) { Text("＋ İlk sınıfı ekle") }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(studio.classes, key = { it.id }) { c ->
                        ClassCard(
                            c,
                            onOpen = { studio.navigate(Screen.ClassDetail(c.id)) },
                            onRename = { renameId = c.id },
                            onDelete = { deleteId = c.id }
                        )
                    }
                }
            }
        }
        if (studio.classes.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { newClassOpen = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text("＋ Sınıf ekle") }
        }
    }

    studio.auditReport?.let { rep ->
        AlertDialog(
            onDismissRequest = { studio.dismissAudit() },
            title = { Text("Veri denetimi") },
            text = { Text(rep, modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = { TextButton(onClick = { studio.dismissAudit() }) { Text("Tamam") } }
        )
    }

    if (newClassOpen) {
        NameDialog(
            title = "Yeni sınıf",
            label = "Sınıf adı (örn. 9-A)",
            initial = "",
            confirmText = "Devam",
            onDismiss = { newClassOpen = false },
            onConfirm = { name ->
                val id = studio.addClass(name)
                if (id == null) {
                    "Ad boş veya bu adla bir sınıf var"
                } else {
                    newClassOpen = false
                    studio.navigate(Screen.Import(id))
                    null
                }
            }
        )
    }
    val rn = renameId?.let { studio.classById(it) }
    if (rn != null) {
        NameDialog(
            title = "Sınıfı yeniden adlandır",
            label = "Sınıf adı",
            initial = rn.name,
            confirmText = "Kaydet",
            onDismiss = { renameId = null },
            onConfirm = { name ->
                if (studio.renameClass(rn.id, name)) {
                    renameId = null
                    null
                } else {
                    "Geçersiz veya kullanılan ad"
                }
            }
        )
    }
    val del = deleteId?.let { studio.classById(it) }
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Sınıf silinsin mi?") },
            text = {
                Column {
                    Text("“${del.name}” ve ${del.students.size} öğrencilik listesi uygulamadan silinir.")
                    Row(
                        Modifier.fillMaxWidth().clickable { alsoFilesClass = !alsoFilesClass }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(checked = alsoFilesClass, onCheckedChange = { alsoFilesClass = it })
                        Text(
                            "Kayıt klasöründeki fotoğraf dosyalarını (Orijinaller dahil) da sil",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (alsoFilesClass) Text(
                        "Bu işlem geri alınamaz.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    studio.deleteClass(del.id, alsoFilesClass)
                    deleteId = null
                    alsoFilesClass = false
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun SummaryCard(studio: StudioViewModel) {
    val classes = studio.classes
    val total = classes.sumOf { it.students.size }
    val done = classes.sumOf { it.capturedCount }
    val missing = total - done
    val retake = classes.sumOf { c -> c.students.count { it.retake } }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("${classes.size} sınıf · $total öğrenci", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$done fotoğraf tamamlandı · $missing bekliyor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (retake > 0) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                    Text("$retake tekrar", modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

private val DoneGreen = Color(0xFF23865B)
private val WarnOrange = Color(0xFFB66A12)

@Composable
private fun ClassCard(
    c: SchoolClass,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val total = c.students.size
    val done = c.capturedCount
    val missing = total - done
    val retake = c.students.count { it.retake }
    val pct = if (total == 0) 0 else (done * 100f / total).toInt()
    val statusColor = when {
        total > 0 && done == total && retake == 0 -> DoneGreen
        retake > 0 -> WarnOrange
        done > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    "$done/$total",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Box {
                    MoreBtn(onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = {
                            menu = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text("Sınıfı sil") }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).padding(top = 3.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.size(5.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("$total öğrenci", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$done çekildi", style = MaterialTheme.typography.labelSmall, color = DoneGreen)
                Text("$missing bekliyor", style = MaterialTheme.typography.labelSmall,
                    color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (retake > 0) {
                    Text("· $retake tekrar", style = MaterialTheme.typography.labelSmall, color = WarnOrange)
                }
            }
        }
    }
}

/** Tek satırlık ad girişi diyaloğu. [onConfirm] hata metni döndürürse gösterilir, null ise kapatma çağıran tarafa aittir. */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> String?
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    label = { Text(label) }, singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
