package com.eokul.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.eokul.studio.core.Quality
import com.eokul.studio.core.Score
import java.io.File

/** Aynı öğrenci için art arda çekilen karelerden en netini / en iyi pozlananı seçer. */
object BurstPick {
    class Shot(val file: File, val eyeOpen: Float)

    /** Kare puanı (0..100). Okunamayan kare -1 alır. */
    fun score(shot: Shot): Int {
        var bmp: Bitmap? = null
        var rotated: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(shot.file.absolutePath, bounds)
            var sample = 1
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 640) sample *= 2
            bmp = BitmapFactory.decodeFile(
                shot.file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return -1
            val deg = when (ExifInterface(shot.file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            rotated = if (deg == 0) bmp else Bitmap.createBitmap(
                bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true
            )
            val w = rotated.width
            val h = rotated.height
            // Yüz kılavuza oturduğu için kadrajın orta bölgesi yüzü içerir
            val box = doubleArrayOf(w * 0.22, h * 0.15, w * 0.78, h * 0.70)
            val px = IntArray(w * h)
            rotated.getPixels(px, 0, w, 0, 0, w, h)
            val q = Quality.analyze(px, w, h, box)
            Score.total(0.0, q.blurRatio, 0.0, 0.0, q.brightness, q.contrast, shot.eyeOpen.toDouble())
        } catch (e: Throwable) {
            -1
        } finally {
            try { if (rotated != null && rotated !== bmp) rotated.recycle() } catch (_: Exception) { }
            try { bmp?.recycle() } catch (_: Exception) { }
        }
    }

    /** En yüksek puanlı kareyi döndürür; diğerlerini siler. Eşitlikte ilk kare. Hiçbiri okunamazsa ilk kare. */
    fun pickBest(shots: List<Shot>): File {
        if (shots.size == 1) return shots[0].file
        var best = 0
        var bestScore = -2
        for (i in shots.indices) {
            val sc = score(shots[i])
            if (sc > bestScore) {
                bestScore = sc
                best = i
            }
        }
        for (i in shots.indices) if (i != best) shots[i].file.delete()
        return shots[best].file
    }
}
