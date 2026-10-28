package com.eokul.studio

import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** Kamera önizlemesindeki anlık yüz (0..1 normalleştirilmiş, dik görüntü koordinatları, ön kamerada aynalanmış). */
class LiveFace(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val roll: Float, val yaw: Float, val ts: Long
) {
    val cx: Float get() = (x1 + x2) / 2f
    val cy: Float get() = (y1 + y2) / 2f
    val h: Float get() = y2 - y1
}

/** Önizleme alanı 3:4 olduğundan birimler: genişlik = 1, yükseklik = 4/3. */
const val PREVIEW_H = 4f / 3f

/** Çekim kılavuzunun geometrisi (genişlik w, yükseklik h piksel / birim). */
class Guide(val left: Float, val top: Float, val width: Float, val height: Float, val hairY: Float, val chinY: Float)

fun guideFor(w: Float, h: Float, s: Settings): Guide {
    val cw0 = w * 0.88f
    val ch = (cw0 * TARGET_H / TARGET_W).coerceAtMost(h * 0.96f)
    val cw = ch * TARGET_W / TARGET_H
    val left = (w - cw) / 2f
    val top = (h - ch) / 2f
    val hair = top + ch * s.topGap.toFloat()
    val chin = hair + ch * s.headFrac.toFloat()
    return Guide(left, top, cw, ch, hair, chin)
}

class LiveEval(val ready: Boolean, val hint: String)

/** Yüzün kılavuza uyup uymadığını değerlendirir (otomatik çekim ve yönlendirme için). Tolerans: [Settings.autoTol]. */
fun evaluateLive(l: LiveFace, s: Settings): LiveEval {
    val t = s.autoTol.toFloat()
    val g = guideFor(1f, PREVIEW_H, s)
    val headH = g.chinY - g.hairY
    val faceBottom = l.y2 * PREVIEW_H
    val faceH = l.h * PREVIEW_H
    val ratio = faceH / headH
    return when {
        Math.abs(l.cx - 0.5f) > 0.10f * t -> LiveEval(false, if (l.cx < 0.5f) "Sağa kayın" else "Sola kayın")
        ratio < 0.78f - 0.20f * t -> LiveEval(false, "Biraz yaklaşın")
        ratio > 0.78f + 0.20f * t -> LiveEval(false, "Biraz uzaklaşın")
        faceBottom < g.chinY - headH * 0.14f * t -> LiveEval(false, "Yüz çok yukarıda")
        faceBottom > g.chinY + headH * 0.14f * t -> LiveEval(false, "Yüz çok aşağıda")
        Math.abs(l.roll) > 10f * t -> LiveEval(false, "Başı dik tutun")
        Math.abs(l.yaw) > 15f * t -> LiveEval(false, "Karşıya bakın")
        else -> LiveEval(true, "Hazır — sabit durun")
    }
}

object LiveFaces {
    val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build()
        )
    }
}
