package com.eokul.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel, studio: StudioViewModel, showRecrop: Boolean, onClose: () -> Unit) {
    BackHandler { onClose() }
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) studio.setRootFolder(uri)
    }
    val cur = vm.settings
    val def = Settings()

    var topGap by remember { mutableStateOf(cur.topGap.toFloat()) }
    var headFrac by remember { mutableStateOf(cur.headFrac.toFloat()) }
    var quality by remember { mutableStateOf(cur.jpegQuality.toFloat()) }
    var minKb by remember { mutableStateOf(cur.minKb.toString()) }
    var regex by remember { mutableStateOf(cur.nameRegex) }
    var outDir by remember { mutableStateOf(cur.outputDir) }
    var embed by remember { mutableStateOf(cur.embedSrgb) }
    var serial by remember { mutableStateOf(cur.serialMode) }
    var keepOrig by remember { mutableStateOf(cur.keepOriginals) }
    var eokulCopy by remember { mutableStateOf(cur.eokulCopy) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): Settings? {
        val kb = minKb.trim().toIntOrNull()
        val dir = outDir.trim()
        val rx = regex.trim()
        val problem: String? = when {
            kb == null || kb !in 1..500 -> "Minimum boyut 1–500 KB arasında olmalı"
            dir.isEmpty() -> "Çıktı klasörü adı boş olamaz"
            dir.any { it in "\\/:*?\"<>|" } || dir == "." || dir == ".." -> "Çıktı klasörü adı geçersiz karakter içeriyor"
            else -> try {
                Regex(rx)
                null
            } catch (e: Exception) {
                "Dosya adı kuralı (regex) geçersiz"
            }
        }
        error = problem
        if (problem != null || kb == null) return null
        return vm.settings.copy(
            serialMode = serial,
            keepOriginals = keepOrig,
            eokulCopy = eokulCopy,
            topGap = (topGap * 100.0).roundToInt() / 100.0,
            headFrac = (headFrac * 100.0).roundToInt() / 100.0,
            jpegQuality = quality.roundToInt(),
            minKb = kb,
            embedSrgb = embed,
            nameRegex = rx,
            outputDir = dir
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Geri") }
            Text("Ayarlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(8.dp))

        // ---------------- Kayıt (sınıf akışı)
        Text("📂  Sınıf kayıtları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(6.dp))
        Text(
            "Kayıt klasörü: ${studio.rootName ?: "seçilmedi"}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.size(4.dp))
        OutlinedButton(onClick = { rootPicker.launch(null) }) { Text("Klasör seç / değiştir") }
        Text(
            "Yapı: Sınıf/12345-Ad Soyad.jpg · Sınıf/Orijinaller/ · E-Okul/Sınıf/12345.jpg",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = serial, onCheckedChange = { serial = it })
            Text("  Seri çekim: kayıttan sonra sıradaki öğrenciye geç", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = keepOrig, onCheckedChange = { keepOrig = it })
            Text("  Tam çözünürlüklü orijinali sakla", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = eokulCopy, onCheckedChange = { eokulCopy = it })
            Text("  E-Okul klasörüne numara adlı kopya yaz", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.size(20.dp))

        // ---------------- Kırpma
        Text("✂  Kırpma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(6.dp))
        Text("Saç üstü boşluk: %${(topGap * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = topGap, onValueChange = { topGap = it }, valueRange = 0.03f..0.20f, steps = 16)
        Text(
            "Saçın tepesi ile fotoğrafın üst kenarı arasındaki sabit boşluk (kırpma yüksekliğinin oranı). " +
                "Tüm öğrencilerde aynı oranda uygulanır.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        Spacer(Modifier.size(12.dp))
        Text("Baş boyutu: %${(headFrac * 100).roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = headFrac, onValueChange = { headFrac = it }, valueRange = 0.50f..0.75f, steps = 24)
        Text(
            "Saç tepesi ile çene arasındaki mesafenin kırpmaya oranı. Yüz çok büyük çıkıyorsa azaltın, " +
                "küçük kalıyorsa artırın.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        Spacer(Modifier.size(6.dp))
        OutlinedButton(onClick = {
            topGap = def.topGap.toFloat()
            headFrac = def.headFrac.toFloat()
        }) { Text("Kırpma varsayılanları") }

        Spacer(Modifier.size(20.dp))

        // ---------------- Çıktı
        Text("💾  Çıktı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(6.dp))
        Text("JPEG kalitesi: ${quality.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = quality, onValueChange = { quality = it }, valueRange = 70f..100f, steps = 29)
        Text(
            "100 önerilir: renk kanalları tam çözünürlükte (4:4:4) saklanır, 300 DPI yazılır.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = minKb, onValueChange = { minKb = it },
            label = { Text("Minimum dosya boyutu (KB)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = outDir, onValueChange = { outDir = it },
            label = { Text("Çıktı klasörü adı") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        OutlinedTextField(
            value = regex, onValueChange = { regex = it },
            label = { Text("e-Okul dosya adı kuralı (regex)") }, singleLine = true,
            supportingText = { Text("Örn: ^\\d{5,11}$  (5–11 haneli sayı) — kaydetmeden önce denetlenir") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = embed, onCheckedChange = { embed = it })
            Text(
                "  🎨  Çıktıya sRGB renk profili göm (önerilir)",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        error?.let {
            Spacer(Modifier.size(10.dp))
            Text("⚠ $it", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.size(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    build()?.let {
                        vm.updateSettings(it)
                        onClose()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Kaydet") }
            Button(
                onClick = {
                    build()?.let {
                        vm.updateSettings(it)
                        onClose()
                        vm.recropUnedited()
                    }
                },
                enabled = showRecrop && vm.items.isNotEmpty() && !vm.busy(),
                modifier = Modifier.weight(1.4f)
            ) { Text("Kaydet ve yeniden kırp") }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            "“Yeniden kırp” yalnızca elle düzenlemediğiniz fotoğrafları günceller; düzenledikleriniz korunur.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        Spacer(Modifier.size(24.dp))
    }
}
