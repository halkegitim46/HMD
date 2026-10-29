package com.eokul.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.eokul.studio.core.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler

private val Bg = Color(0xFF101014)
private val Warn = Color(0xFFE0AF68)
private val Dim = Color(0xFFB0B6C8)

@Composable
fun ReviewScreen(studio: StudioViewModel) {
    BackHandler { studio.discardCapture() }
    val cap = studio.capture
    if (cap == null) {
        Box(Modifier.fillMaxSize().background(Bg))
        return
    }
    val cls = studio.classById(cap.classId)
    val student = cls?.students?.firstOrNull { it.no == cap.no }
    val s = cap.session
    val preview = rememberPreview(s, studio.settings)
    val quality by produceState<Quality.Report?>(null, s, s?.box, s?.loading) {
        val ss = s
        val px = ss?.pixels
        if (ss == null || px == null || ss.loading) {
            value = null
        } else {
            val box = ss.box
            val iw = ss.iw
            val ih = ss.ih
            value = withContext(Dispatchers.Default) { ImageOps.quality(px, iw, ih, box) }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp)) {
        Text(
            student?.name ?: cap.no, color = Color.White,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
        )
        Text(
            "${cap.no}  ·  ${cls?.name ?: ""}" + if (cap.fromSaved) "  ·  kayıtlı orijinalden" else "",
            color = Dim, fontSize = 12.sp
        )
        Spacer(Modifier.size(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val err = cap.error
            when {
                err != null -> Text("Fotoğraf işlenemedi: $err", color = Warn)
                cap.loading || s == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(8.dp))
                        Text("Yüz algılanıyor…", color = Dim)
                    }
                }
                else -> {
                    val pb = preview?.first
                    if (pb != null) {
                        Image(
                            bitmap = pb.asImageBitmap(),
                            contentDescription = "Önizleme",
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxHeight().aspectRatio(TARGET_W.toFloat() / TARGET_H)
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        if (s != null && !cap.loading) {
            val warnings = ArrayList<String>()
            if (s.faces.isEmpty()) warnings.add("⚠ Yüz bulunamadı — alanı elle ayarlayın veya yeniden çekin.")
            if (s.faces.size > 1) warnings.add("⚠ ${s.faces.size} yüz algılandı — en büyüğü seçildi. Doğruluğunu kontrol edin.")
            if (s.box.width < TARGET_W) warnings.add("⚠ Kırpılan alan hedef çözünürlükten küçük; görüntü bulanık olabilir.")
            val q = quality
            if (q != null) {
                if (q.blurry()) warnings.add("⚠ Fotoğraf bulanık görünüyor — yeniden çekmeniz önerilir.")
                if (q.dark()) warnings.add("⚠ Fotoğraf çok karanlık — daha aydınlık bir yerde çekin.")
                if (q.bright()) warnings.add("⚠ Fotoğraf aşırı parlak — ışığı azaltın.")
            }
            val f0 = s.faces.firstOrNull()
            if (f0 != null) {
                if (Math.abs(f0.roll) > 10f) warnings.add("⚠ Baş yana eğik (${Math.abs(f0.roll).toInt()}°) — başı dik tutturun.")
                if (Math.abs(f0.yaw) > 15f) warnings.add("⚠ Yüz kameraya dönük değil (${Math.abs(f0.yaw).toInt()}°) — karşıdan çekin.")
            }
            androidx.compose.runtime.LaunchedEffect(warnings.size) { cap.warned = warnings.isNotEmpty() }
            for (w in warnings) Text(w, color = Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            val kb = (preview?.second ?: 0) / 1024.0
            Text(
                "Çıktı: ${TARGET_W}×${TARGET_H} px · ${"%.1f".format(kb)} KB · kaynak ${s.iw}×${s.ih}",
                color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.size(8.dp))
        val pad = PaddingValues(horizontal = 4.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { studio.discardCapture() }, modifier = Modifier.weight(1f),
                enabled = !cap.saving, contentPadding = pad
            ) { Text(if (cap.fromSaved) "İptal" else "↺ Yeniden çek", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { studio.openCaptureEditor() }, modifier = Modifier.weight(1f),
                enabled = s != null && !cap.loading && !cap.saving, contentPadding = pad
            ) { Text("✂ Düzenle", fontSize = 12.sp) }
            Button(
                onClick = { studio.saveCapture() }, modifier = Modifier.weight(1.4f),
                enabled = s != null && !cap.loading && !cap.saving, contentPadding = pad
            ) { Text(if (cap.saving) "Kaydediliyor…" else "✅ Kaydet", fontSize = 13.sp) }
        }
    }
}
