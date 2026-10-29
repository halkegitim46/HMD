package com.eokul.studio

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eokul.studio.core.RosterParser
import com.eokul.studio.core.RosterParser.CaseMode
import com.eokul.studio.core.RosterParser.Delimiter

private fun delimLabel(d: Delimiter): String = when (d) {
    Delimiter.AUTO -> "Otomatik"
    Delimiter.TAB -> "Sekme"
    Delimiter.SEMICOLON -> "Noktalı virgül ;"
    Delimiter.COMMA -> "Virgül ,"
    Delimiter.PIPE -> "Dikey çizgi |"
    Delimiter.DASH -> "Tire -"
    Delimiter.SPACE -> "Boşluk"
}

@Composable
fun ImportScreen(studio: StudioViewModel, classId: String?) {
    val ctx = LocalContext.current
    val cls = studio.classById(classId)
    var text by remember { mutableStateOf("") }
    var delim by remember { mutableStateOf(Delimiter.AUTO) }
    var surnameFirst by remember { mutableStateOf(false) }
    var caseMode by remember { mutableStateOf(CaseMode.KEEP) }
    var mode by remember { mutableStateOf(MergeMode.UPSERT) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    error = "Dosya boş"
                } else {
                    if (com.eokul.studio.core.SheetReader.looksLikeExcel(bytes)) {
                        text = com.eokul.studio.core.SheetReader.readText(bytes)
                        if (text.isBlank()) error = "Excel sayfasında liste bulunamadı"
                        else error = null
                    } else {
                        val t = decodeText(bytes)
                        text = if (com.eokul.studio.core.SheetReader.looksLikeHtml(t)) com.eokul.studio.core.SheetReader.htmlToText(t) else t
                        error = if (text.isBlank()) "Dosyada liste bulunamadı" else null
                    }
                }
            } catch (e: Throwable) {
                error = "Dosya okunamadı: ${e.javaClass.simpleName} ${e.message ?: ""}".take(160)
            }
        }
    }

    val result = remember(text, delim, surnameFirst, caseMode) {
        if (text.isBlank()) null else RosterParser.parse(text, delim, surnameFirst, caseMode)
    }
    val valid = result?.valid() ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                BackBtn(onClick = { studio.back() }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    if (cls != null) "${cls.name} · liste ekle" else "Yeni sınıf · liste ekle",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            if (cls == null) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it; error = null },
                    label = { Text("Sınıf adı (örn. 9-A)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(10.dp))
            }

            Text(
                "Excel veya e-Okul’dan numara ve ad soyad sütunlarını kopyalayıp yapıştırın. " +
                    "Her satır bir öğrenci olmalı.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (t.isNullOrBlank()) error = "Panoda metin yok" else {
                        text = t
                        error = null
                    }
                }) { Text("📋 Panodan yapıştır") }
                OutlinedButton(onClick = { filePicker.launch(
                    arrayOf("*/*")
                ) }) {
                    Text("📄 Excel / CSV")
                }
                if (text.isNotEmpty()) OutlinedButton(onClick = { text = "" }) { Text("Temizle") }
            }
            Spacer(Modifier.size(6.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it; error = null },
                label = { Text("Liste") },
                placeholder = { Text("12345\tAyşe Yılmaz\n12346\tMehmet Kaya") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp)
            )

            Spacer(Modifier.size(10.dp))
            Text("Ayraç", style = MaterialTheme.typography.labelLarge)
            ChipRow(Delimiter.values().toList(), delim, { delimLabel(it) }) { delim = it }
            if (result != null && delim == Delimiter.AUTO) {
                Text("Algılanan: ${delimLabel(result.detected)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = surnameFirst, onCheckedChange = { surnameFirst = it })
                Text("Soyad önce yazılmış (Yılmaz Ayşe → Ayşe Yılmaz)", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Harf biçimi", style = MaterialTheme.typography.labelLarge)
            ChipRow(CaseMode.values().toList(), caseMode, {
                when (it) {
                    CaseMode.KEEP -> "Olduğu gibi"
                    CaseMode.TITLE -> "Ad Soyad"
                    CaseMode.UPPER -> "AD SOYAD"
                }
            }) { caseMode = it }

            if (cls != null && cls.students.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text("Mevcut listeyle", style = MaterialTheme.typography.labelLarge)
                ChipRow(MergeMode.values().toList(), mode, {
                    when (it) {
                        MergeMode.ADD -> "Yalnızca yeni ekle"
                        MergeMode.UPSERT -> "Ekle + adı güncelle"
                        MergeMode.REPLACE -> "Listeyi değiştir"
                    }
                }) { mode = it }
                if (mode == MergeMode.REPLACE) {
                    Text(
                        "Listede olmayan öğrenciler bu sınıftan çıkarılır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
            if (result != null) {
                Text(
                    "${valid.size} geçerli satır" + if (result.errorCount() > 0) "  ·  ${result.errorCount()} hatalı" else "",
                    fontWeight = FontWeight.Bold
                )
                if (result.skippedHeaders > 0) {
                    Text("Başlık satırı atlandı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(4.dp))
                val shownRows = result.rows.take(150)
                for (r in shownRows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        if (r.ok()) {
                            Text(r.no, modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.bodyMedium)
                            Text(r.name, modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "Satır ${r.line}: ${r.error}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (result.rows.size > shownRows.size) {
                    Text("… ve ${result.rows.size - shownRows.size} satır daha", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let {
                Spacer(Modifier.size(8.dp))
                Text("⚠ $it", color = MaterialTheme.colorScheme.error)
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { studio.back() }, modifier = Modifier.weight(1f)) { Text("Vazgeç") }
                Button(
                    enabled = valid.isNotEmpty(),
                    modifier = Modifier.weight(2f),
                    onClick = {
                        val id = studio.importRoster(classId, newName, valid, mode)
                        if (id == null) {
                            error = if (cls == null) "Sınıf adı boş veya bu adla bir sınıf var" else "İçe aktarılamadı"
                        } else if (classId == null) {
                            studio.replace(Screen.ClassDetail(id))
                        } else {
                            studio.back()
                        }
                    }
                ) { Text("İçe aktar (${valid.size})") }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    // Dar ekranda taşmaması için sarmalanan satırlar
    val rows = options.chunked(3)
    for (r in rows) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            for (o in r) {
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

/** UTF-8 (BOM'lu olabilir); geçersizse Türkçe Excel CSV'lerinin kullandığı Windows-1254. */
private fun decodeText(bytes: ByteArray): String {
    return try {
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        String(bytes, java.nio.charset.Charset.forName("windows-1254"))
    }
}
