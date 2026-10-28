package com.eokul.studio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eokul.studio.core.Crop

data class Settings(
    val topGap: Double = 0.10,
    val headFrac: Double = 0.62,
    val jpegQuality: Int = 100,
    val minKb: Int = 20,
    val embedSrgb: Boolean = true,
    val nameRegex: String = "^\\d{5,11}$",
    val outputDir: String = "E_Okul_Kayitlar",
    /** Sınıf klasörlerinin oluşturulacağı ana klasör (SAF ağaç URI'si). */
    val rootUri: String? = null,
    /** Onaydan sonra sıradaki öğrenciye otomatik geç. */
    val serialMode: Boolean = true,
    /** Tam çözünürlüklü orijinal fotoğrafı "Orijinaller" klasöründe sakla. */
    val keepOriginals: Boolean = true,
    /** E-Okul klasörüne yalnızca numara adlı kopya yaz (12345.jpg). */
    val eokulCopy: Boolean = true
) {
    fun cropParams(): Crop.Params {
        val p = Crop.Params()
        p.topGap = topGap
        p.headFrac = headFrac
        return p
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("eokul_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        val regex = sp.getString("nameRegex", null)?.takeIf { r ->
            try {
                Regex(r)
                true
            } catch (e: Exception) {
                false
            }
        } ?: d.nameRegex
        val outDir = sp.getString("outputDir", null)?.trim()?.takeIf { it.isNotEmpty() } ?: d.outputDir
        return Settings(
            topGap = (sp.getString("topGap", null)?.toDoubleOrNull() ?: d.topGap).coerceIn(0.02, 0.25),
            headFrac = (sp.getString("headFrac", null)?.toDoubleOrNull() ?: d.headFrac).coerceIn(0.45, 0.80),
            jpegQuality = sp.getInt("jpegQuality", d.jpegQuality).coerceIn(70, 100),
            minKb = sp.getInt("minKb", d.minKb).coerceIn(1, 500),
            embedSrgb = sp.getBoolean("embedSrgb", d.embedSrgb),
            nameRegex = regex,
            outputDir = outDir,
            rootUri = sp.getString("rootUri", null)?.takeIf { it.isNotBlank() },
            serialMode = sp.getBoolean("serialMode", d.serialMode),
            keepOriginals = sp.getBoolean("keepOriginals", d.keepOriginals),
            eokulCopy = sp.getBoolean("eokulCopy", d.eokulCopy)
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("topGap", s.topGap.toString())
            .putString("headFrac", s.headFrac.toString())
            .putInt("jpegQuality", s.jpegQuality)
            .putInt("minKb", s.minKb)
            .putBoolean("embedSrgb", s.embedSrgb)
            .putString("nameRegex", s.nameRegex)
            .putString("outputDir", s.outputDir)
            .putString("rootUri", s.rootUri)
            .putBoolean("serialMode", s.serialMode)
            .putBoolean("keepOriginals", s.keepOriginals)
            .putBoolean("eokulCopy", s.eokulCopy)
            .apply()
    }
}

/** Tüm ekranların paylaştığı ayar durumu (Compose tarafından izlenir). */
object SettingsState {
    private var prefs: Prefs? = null
    private var state by mutableStateOf(Settings())

    val current: Settings get() = state

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = Prefs(ctx.applicationContext)
        prefs = p
        state = p.load()
    }

    fun update(s: Settings) {
        state = s
        prefs?.save(s)
    }
}
