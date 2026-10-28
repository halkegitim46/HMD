package com.eokul.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val studio: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EOkulTheme {
                AppRoot(vm, studio)
            }
        }
    }

    override fun onStop() {
        vm.persistNow()
        studio.persistNow()
        super.onStop()
    }
}

@Composable
fun EOkulTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF062E6F),
            primaryContainer = Color(0xFF1F3A63),
            onPrimaryContainer = Color(0xFFD8E6FF)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3B6FD4),
            primaryContainer = Color(0xFFDCE7FF),
            onPrimaryContainer = Color(0xFF0B2A66)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(vm: AppViewModel, studio: StudioViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        studio.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    // Ana geri tuşu: sınıf akışında yığını geri sarar. Toplu kırpma içindeki düzenleyici / ayarlar kendi
    // BackHandler'larını (daha derinde oldukları için öncelikli) kullanır.
    BackHandler(enabled = studio.stack.size > 1) { studio.handleBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val sc = studio.screen) {
                is Screen.Home -> HomeScreen(studio)
                is Screen.ClassDetail -> ClassScreen(studio, sc.classId)
                is Screen.Import -> ImportScreen(studio, sc.classId)
                is Screen.Camera -> CameraScreen(studio, sc.classId, sc.no)
                is Screen.Review -> ReviewScreen(studio)
                is Screen.CaptureEditor -> CaptureEditorScreen(studio)
                is Screen.Settings -> SettingsScreen(vm, studio, showRecrop = false, onClose = { studio.back() })
                is Screen.Batch -> when (vm.route) {
                    Route.Gallery -> GalleryScreen(vm, onBack = { studio.back() })
                    Route.Editor -> EditorScreen(vm)
                    Route.Settings -> SettingsScreen(vm, studio, showRecrop = true, onClose = { vm.closeSettings() })
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
        }
    }

    val warning = vm.nameWarning
    if (warning != null) {
        val sample = warning.bad.take(5).joinToString(", ") + if (warning.bad.size > 5) " …" else ""
        AlertDialog(
            onDismissRequest = { vm.dismissNameWarning() },
            title = { Text("Dosya adı uyarısı") },
            text = {
                Text(
                    "${warning.bad.size} dosyanın adı e-Okul kuralına uymuyor:\n$sample\n\n" +
                        "Kural: ${vm.settings.nameRegex}\n\nYine de kaydedilsin mi?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmNameWarning() }) { Text("Yine de kaydet") } },
            dismissButton = { TextButton(onClick = { vm.dismissNameWarning() }) { Text("Vazgeç") } }
        )
    }

    if (vm.failures.isNotEmpty()) {
        val fails = vm.failures
        val detail = fails.take(8).joinToString("\n") { (n, e) -> "• $n: ${e.take(70)}" } +
            if (fails.size > 8) "\n… ve ${fails.size - 8} tane daha" else ""
        AlertDialog(
            onDismissRequest = { vm.dismissFailures() },
            title = { Text("Kaydetme hataları") },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { vm.dismissFailures() }) { Text("Tamam") } }
        )
    }

    if (vm.confirmResetAll) {
        AlertDialog(
            onDismissRequest = { vm.confirmResetAll = false },
            title = { Text("Tüm kırpmaları sıfırla") },
            text = { Text("Elle yapılan düzenlemeler silinecek ve kırpmalar yeniden otomatik hesaplanacak. Devam edilsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.confirmResetAll = false
                    vm.resetAllCrops()
                }) { Text("Sıfırla") }
            },
            dismissButton = { TextButton(onClick = { vm.confirmResetAll = false }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun CaptureEditorScreen(studio: StudioViewModel) {
    val cap = studio.capture
    val st = cap?.let { c -> studio.classById(c.classId)?.students?.firstOrNull { it.no == c.no } }
    EditorContent(
        s = cap?.session,
        title = if (cap == null) "" else "${cap.no} · ${st?.name ?: ""}",
        settings = studio.settings,
        onCancel = { studio.cancelCaptureEditor() },
        onApply = { studio.applyCaptureEditor() },
        onResetFace = { studio.captureEditorResetToFace() },
        onPickFace = { studio.captureEditorPickFace(it) },
        onPrev = null,
        onNext = null,
        applyLabel = "✅ Uygula"
    )
}
