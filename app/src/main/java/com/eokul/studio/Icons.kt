package com.eokul.studio

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 48 dp dokunma alanlı, 28 dp'lik belirgin simge düğmesi. */
@Composable
fun IconBtn(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, modifier = modifier.size(52.dp), enabled = enabled) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun BackBtn(onClick: () -> Unit, tint: Color = LocalContentColor.current) =
    IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Geri", onClick, tint = tint)

@Composable
fun CloseBtn(onClick: () -> Unit, tint: Color = Color.White) =
    IconBtn(Icons.Filled.Close, "Kapat", onClick, tint = tint)

@Composable
fun MoreBtn(onClick: () -> Unit, tint: Color = LocalContentColor.current) =
    IconBtn(Icons.Filled.MoreVert, "Diğer işlemler", onClick, tint = tint)

@Composable
fun SettingsBtn(onClick: () -> Unit, tint: Color = LocalContentColor.current) =
    IconBtn(Icons.Filled.Settings, "Ayarlar", onClick, tint = tint)

@Composable
fun PrevBtn(onClick: () -> Unit, enabled: Boolean, tint: Color = Color.White) =
    IconBtn(Icons.Filled.KeyboardArrowLeft, "Önceki öğrenci", onClick, tint = if (enabled) tint else Color.Gray, enabled = enabled)

@Composable
fun NextBtn(onClick: () -> Unit, enabled: Boolean, tint: Color = Color.White) =
    IconBtn(Icons.Filled.KeyboardArrowRight, "Sonraki öğrenci", onClick, tint = if (enabled) tint else Color.Gray, enabled = enabled)
