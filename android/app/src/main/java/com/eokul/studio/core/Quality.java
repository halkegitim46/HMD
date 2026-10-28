package com.eokul.studio.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Çekim kalitesi ölçümleri ve uyarı kuralları.
 *
 * Eşikler deneme amaçlıdır; gerçek telefonda "hassasiyet" ayarıyla (0 gevşek, 1 normal, 2 sıkı) ayarlanır.
 */
public final class Quality {
    private Quality() {}

    public static final String NO_FACE = "NO_FACE";
    public static final String MULTI_FACE = "MULTI_FACE";
    public static final String EYES_CLOSED = "EYES_CLOSED";
    public static final String HEAD_TURNED = "HEAD_TURNED";
    public static final String HEAD_TILT = "HEAD_TILT";
    public static final String HEAD_PITCH = "HEAD_PITCH";
    public static final String BLUR = "BLUR";
    public static final String DARK = "DARK";
    public static final String BRIGHT = "BRIGHT";
    public static final String SMALL = "SMALL";
    public static final String CUT = "CUT";

    /** Ölçümler. Bilinmeyen değerler için NaN / -1 kullanılır. */
    public static final class Metrics {
        public int faceCount;
        public double eyeOpen = -1;     // iki gözün küçüğü, 0..1; bilinmiyorsa -1
        public double yaw = 0, pitch = 0, roll = 0;
        public double sharpness = Double.NaN;
        public double luma = Double.NaN;
        public double clipped = 0;      // yüz bölgesinde patlamış (≥250) piksel oranı
        public double cropWidth = Double.NaN;   // kırpma kutusunun genişliği (çalışma görüntüsü pikseli)
        public boolean faceCut = false;         // yüz kutusu görüntü kenarına değiyor
    }

    private static final double[] EYE = {0.20, 0.30, 0.45};
    private static final double[] YAW = {25, 18, 12};
    private static final double[] ROLL = {18, 12, 8};
    private static final double[] PITCH = {22, 16, 10};
    private static final double[] BLUR_T = {25, 45, 70};
    private static final double[] DARK_T = {45, 65, 85};
    private static final double[] BRIGHT_T = {225, 210, 195};
    private static final double[] CLIP_T = {0.25, 0.15, 0.08};
    private static final double[] SMALL_T = {1.0, 1.5, 2.0};

    public static List<String> evaluate(Metrics m, int sensitivity, int targetW) {
        int s = Math.max(0, Math.min(2, sensitivity));
        List<String> w = new ArrayList<>();
        if (m.faceCount == 0) {
            w.add(NO_FACE);
            return w;   // yüz yokken diğer ölçümler anlamsız
        }
        if (m.faceCount > 1) w.add(MULTI_FACE);
        if (m.eyeOpen >= 0 && m.eyeOpen < EYE[s]) w.add(EYES_CLOSED);
        if (Math.abs(m.yaw) > YAW[s]) w.add(HEAD_TURNED);
        if (Math.abs(m.roll) > ROLL[s]) w.add(HEAD_TILT);
        if (Math.abs(m.pitch) > PITCH[s]) w.add(HEAD_PITCH);
        if (!Double.isNaN(m.sharpness) && m.sharpness < BLUR_T[s]) w.add(BLUR);
        if (!Double.isNaN(m.luma)) {
            if (m.luma < DARK_T[s]) w.add(DARK);
            else if (m.luma > BRIGHT_T[s] || m.clipped > CLIP_T[s]) w.add(BRIGHT);
        }
        if (!Double.isNaN(m.cropWidth) && m.cropWidth < targetW * SMALL_T[s]) w.add(SMALL);
        if (m.faceCut) w.add(CUT);
        return w;
    }

    // ------------------------------------------------------------------ ölçümler
    private static final int SAMPLE = 128;

    /** Yüz bölgesini en fazla 128 px'e küçültüp gri tonlamaya çevirir. */
    private static double[] grayRegion(int[] argb, int iw, int ih, double[] box, int[] outSize) {
        int x1 = (int) Math.max(0, Math.floor(box[0])), y1 = (int) Math.max(0, Math.floor(box[1]));
        int x2 = (int) Math.min(iw, Math.ceil(box[2])), y2 = (int) Math.min(ih, Math.ceil(box[3]));
        int rw = x2 - x1, rh = y2 - y1;
        if (rw < 8 || rh < 8) return null;
        double k = Math.min(1.0, (double) SAMPLE / Math.max(rw, rh));
        int dw = Math.max(4, (int) Math.round(rw * k)), dh = Math.max(4, (int) Math.round(rh * k));
        double sx = (double) rw / dw, sy = (double) rh / dh;
        double[] g = new double[dw * dh];
        for (int y = 0; y < dh; y++) {
            double ya = y * sy, yb = ya + sy;
            int ys = (int) Math.floor(ya), ye = Math.min(rh, (int) Math.ceil(yb));
            for (int x = 0; x < dw; x++) {
                double xa = x * sx, xb = xa + sx;
                int xs = (int) Math.floor(xa), xe = Math.min(rw, (int) Math.ceil(xb));
                double acc = 0, ws = 0;
                for (int yy = ys; yy < ye; yy++) {
                    double wy = Math.min(yb, yy + 1) - Math.max(ya, yy);
                    for (int xx = xs; xx < xe; xx++) {
                        double wx = Math.min(xb, xx + 1) - Math.max(xa, xx);
                        int p = argb[(y1 + yy) * iw + x1 + xx];
                        double lum = 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF);
                        acc += lum * wx * wy;
                        ws += wx * wy;
                    }
                }
                g[y * dw + x] = acc / ws;
            }
        }
        outSize[0] = dw;
        outSize[1] = dh;
        return g;
    }

    /** Yüz bölgesinde Laplace varyansı (büyük = keskin). Bölge 128 px'e normalize edildiği için ölçekten bağımsızdır. */
    public static double sharpness(int[] argb, int iw, int ih, double[] faceBox) {
        int[] sz = new int[2];
        double[] g = grayRegion(argb, iw, ih, faceBox, sz);
        if (g == null) return Double.NaN;
        int w = sz[0], h = sz[1];
        if (w < 3 || h < 3) return Double.NaN;
        double sum = 0, sum2 = 0;
        int n = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                double l = g[(y - 1) * w + x] + g[(y + 1) * w + x] + g[y * w + x - 1] + g[y * w + x + 1]
                        - 4 * g[y * w + x];
                sum += l;
                sum2 += l * l;
                n++;
            }
        }
        double mean = sum / n;
        return sum2 / n - mean * mean;
    }

    /** Yüz bölgesinin ortalama parlaklığı (0..255). */
    public static double meanLuma(int[] argb, int iw, int ih, double[] faceBox) {
        int[] sz = new int[2];
        double[] g = grayRegion(argb, iw, ih, faceBox, sz);
        if (g == null) return Double.NaN;
        double s = 0;
        for (double v : g) s += v;
        return s / g.length;
    }

    /** Yüz bölgesinde parlaklığı ≥250 olan piksellerin oranı. */
    public static double clippedFraction(int[] argb, int iw, int ih, double[] faceBox) {
        int[] sz = new int[2];
        double[] g = grayRegion(argb, iw, ih, faceBox, sz);
        if (g == null) return 0;
        int c = 0;
        for (double v : g) if (v >= 250) c++;
        return (double) c / g.length;
    }
}
