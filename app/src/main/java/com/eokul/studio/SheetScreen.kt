package com.eokul.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberThumb(classId: String, s: Student): Bitmap? {
    val ctx = LocalContext.current
    val b by produceState<Bitmap?>(null, classId, s.no, s.updatedAt, s.captured) {
        value = if (!s.captured) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(Naming.privatePhoto(ctx, classId, s.no).absolutePath)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    return b
}

/** Sınıfın tüm fotoğraflarını ızgarada gösterir; tek dokunuşla büyütüp yeniden çekme / düzenleme. */
@Composable
fun SheetScreen(studio: StudioViewModel, classId: String) {
    val cls = studio.classById(classId)
    if (cls == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    var onlyMissing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Student?>(null) }
    val shown = if (onlyMissing) cls.students.filter { !it.captured } else cls.students

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                BackBtn(onClick = { studio.back() }, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${cls.name} · kontrol", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${cls.capturedCount}/${cls.students.size} fotoğraf",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                FilterChip(
                    selected = onlyMissing, onClick = { onlyMissing = !onlyMissing },
                    label = { Text("Yalnız eksikler") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.no }) { s ->
                val thumb = rememberThumb(cls.id, s)
                Column(Modifier.clickable { selected = s }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(TARGET_W.toFloat() / TARGET_H)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22888888))
                            .border(
                                if (s.captured) 0.dp else 1.dp,
                                if (s.captured) Color.Transparent else MaterialTheme.colorScheme.error,
                                RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(), contentDescription = s.name,
                                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(if (s.captured) "…" else "📷", fontSize = 22.sp)
                        }
                    }
                    Text(s.no, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(s.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    val sel = selected?.let { n -> cls.students.firstOrNull { it.no == n.no } }
    if (sel != null) {
        val thumb = rememberThumb(cls.id, sel)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${sel.no} · ${sel.name}") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(), contentDescription = sel.name,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.height(280.dp).aspectRatio(TARGET_W.toFloat() / TARGET_H)
                        )
                    } else {
                        Text("Henüz fotoğraf çekilmemiş")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    studio.openCamera(cls.id, sel.no)
                }) { Text(if (sel.captured) "Yeniden çek" else "Fotoğraf çek") }
            },
            dismissButton = {
                Row {
                    if (sel.captured) {
                        TextButton(onClick = {
                            selected = null
                            studio.editSavedCrop(cls.id, sel.no)
                        }) { Text("Kırpmayı düzenle") }
                    }
                    TextButton(onClick = { selected = null }) { Text("Kapat") }
                }
            }
        )
    }
}
