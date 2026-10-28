package com.eokul.studio

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun CameraScreen(studio: StudioViewModel, classId: String, no: String) {
    BackHandler { studio.back() }
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val cls = studio.classById(classId)
    val student = cls?.students?.firstOrNull { it.no == no }
    if (cls == null || student == null || activity == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val index = cls.students.indexOfFirst { it.no == no }

    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPerm = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA)
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) studio.onPhotoReady(classId, no, uri, null)
    }

    var lensBack by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var camError by remember { mutableStateOf<String?>(null) }

    val selector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            keepScreenOn = true
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setResolutionSelector(selector).build()
    }

    DisposableEffect(lensBack, hasPerm) {
        if (!hasPerm) {
            onDispose { }
        } else {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().setResolutionSelector(selector).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.unbindAll()
                    val cs = if (lensBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    camera = provider.bindToLifecycle(activity, cs, preview, imageCapture)
                    camError = null
                } catch (e: Exception) {
                    camError = "Kamera açılamadı: ${e.message ?: e.javaClass.simpleName}"
                    if (!lensBack) lensBack = true
                }
            }, ContextCompat.getMainExecutor(ctx))
            onDispose {
                try {
                    if (future.isDone) future.get().unbindAll()
                } catch (e: Exception) {
                    // yoksay
                }
                camera = null
            }
        }
    }
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(camera, zoom) {
        camera?.cameraControl?.setLinearZoom(zoom)
    }

    fun shoot() {
        if (capturing) return
        capturing = true
        val dir = File(ctx.cacheDir, "capture")
        dir.mkdirs()
        val file = File(dir, "${no}_${System.currentTimeMillis()}.jpg")
        previewView.display?.let { imageCapture.targetRotation = it.rotation }
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    studio.onPhotoReady(classId, no, Uri.fromFile(file), file)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    camError = "Çekim başarısız: ${exception.message ?: "hata"}"
                    file.delete()
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Öğrenci başlığı
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { studio.back() }) { Text("✕", color = Color.White, fontSize = 18.sp) }
            Column(Modifier.weight(1f)) {
                Text(
                    student.name, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${student.no}  ·  ${cls.name}  ·  ${index + 1}/${cls.students.size}" +
                        if (student.captured) "  ·  ✅" else "",
                    color = Color(0xFFB0B6C8), fontSize = 12.sp
                )
            }
            TextButton(onClick = { studio.cameraMove(classId, no, -1) }, enabled = index > 0) {
                Text("‹", color = if (index > 0) Color.White else Color.Gray, fontSize = 22.sp)
            }
            TextButton(onClick = { studio.cameraMove(classId, no, 1) }, enabled = index < cls.students.size - 1) {
                Text("›", color = if (index < cls.students.size - 1) Color.White else Color.Gray, fontSize = 22.sp)
            }
        }

        // ---- Önizleme (3:4) ve kılavuz
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (hasPerm) {
                Box(Modifier.fillMaxHeight().aspectRatio(3f / 4f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    GuideOverlay(studio.settings)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Kamera izni gerekli", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Fotoğraf çekmek için kamera izni verin veya galeriden bir fotoğraf seçin.",
                        color = Color(0xFFB0B6C8), modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(onClick = {
                        if (askedOnce) {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", ctx.packageName, null))
                            )
                        } else {
                            permLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) { Text(if (askedOnce) "Uygulama ayarlarını aç" else "İzin ver") }
                }
            }
            if (capturing) CircularProgressIndicator(Modifier.size(48.dp))
        }

        camError?.let {
            Text(it, color = Color(0xFFE0AF68), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }

        if (hasPerm) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", color = Color.White)
                Slider(
                    value = zoom, onValueChange = { zoom = it }, valueRange = 0f..0.6f,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        }

        // ---- Alt düğmeler
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { galleryPicker.launch(arrayOf("image/*")) }) {
                Text("🖼 Galeri", color = Color.White, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    torchOn = !torchOn
                },
                enabled = hasPerm && lensBack
            ) { Text(if (torchOn) "🔦 Açık" else "🔦 Flaş", color = Color.White, fontSize = 12.sp) }
            Button(
                onClick = { shoot() },
                enabled = hasPerm && camera != null && !capturing,
                modifier = Modifier.size(72.dp)
            ) { Text("●", fontSize = 28.sp) }
            OutlinedButton(
                onClick = {
                    torchOn = false
                    lensBack = !lensBack
                },
                enabled = hasPerm
            ) { Text("🔄 Çevir", color = Color.White, fontSize = 12.sp) }
            OutlinedButton(onClick = { studio.cameraMove(classId, no, 1) }, enabled = index < cls.students.size - 1) {
                Text("Atla ›", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** Çerçeve: e-Okul kırpma alanı, baş ovali ve çene çizgisi (ayarlardaki oranlara göre). */
@Composable
private fun GuideOverlay(s: Settings) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Kırpma alanı: genişliğin %88'i, 133:171 oranında
        val cw = w * 0.88f
        val ch = (cw * TARGET_H / TARGET_W).coerceAtMost(h * 0.96f)
        val cwAdj = ch * TARGET_W / TARGET_H
        val left = (w - cwAdj) / 2f
        val top = (h - ch) / 2f

        val dim = Color.Black.copy(alpha = 0.40f)
        drawRect(dim, Offset(0f, 0f), Size(w, top))
        drawRect(dim, Offset(0f, top + ch), Size(w, h - top - ch))
        drawRect(dim, Offset(0f, top), Size(left, ch))
        drawRect(dim, Offset(left + cwAdj, top), Size(w - left - cwAdj, ch))
        drawRect(Color.White.copy(alpha = 0.8f), Offset(left, top), Size(cwAdj, ch), style = Stroke(width = 2f))

        // Saç tepesi ve çene çizgileri
        val hairY = top + ch * s.topGap.toFloat()
        val chinY = hairY + ch * s.headFrac.toFloat()
        val headH = chinY - hairY
        val headW = headH * 0.74f
        val cx = w / 2f
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        drawRoundRect(
            color = Color(0xFF9ECE6A),
            topLeft = Offset(cx - headW / 2f, hairY),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headW / 2f, headH / 2f),
            style = Stroke(width = 3f, pathEffect = dash)
        )
        drawLine(Color(0xFF9ECE6A).copy(alpha = 0.7f), Offset(left, chinY), Offset(left + cwAdj, chinY), strokeWidth = 1.5f)
        // Omuz çizgisi için yumuşak ipucu
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(left, top + ch * 0.86f), Offset(left + cwAdj, top + ch * 0.86f),
            strokeWidth = 1f, pathEffect = dash
        )
    }
}
