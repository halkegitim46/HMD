import com.eokul.studio.core.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Python v17.6 referansıyla karşılaştırma harnesi (yalnızca test; APK'ya girmez). */
public class CoreTest {
    static int[] readRgb(String path, int w, int h) throws IOException {
        byte[] b = Files.readAllBytes(Paths.get(path));
        int[] px = new int[w * h];
        for (int i = 0; i < w * h; i++)
            px[i] = 0xFF000000 | ((b[i * 3] & 0xFF) << 16) | ((b[i * 3 + 1] & 0xFF) << 8) | (b[i * 3 + 2] & 0xFF);
        return px;
    }

    static void writeRgb(String path, int[] px) throws IOException {
        byte[] b = new byte[px.length * 3];
        for (int i = 0; i < px.length; i++) {
            b[i * 3] = (byte) (px[i] >> 16);
            b[i * 3 + 1] = (byte) (px[i] >> 8);
            b[i * 3 + 2] = (byte) px[i];
        }
        Files.write(Paths.get(path), b);
    }

    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "jpeg": {   // jpeg in.rgb w h q icc|- minBytes out.jpg
                int w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
                byte[] icc = a[5].equals("-") ? null : Files.readAllBytes(Paths.get(a[5]));
                byte[] out = JpegEncoder.encode(readRgb(a[1], w, h), w, h, Integer.parseInt(a[4]), 300, icc,
                        Integer.parseInt(a[6]));
                Files.write(Paths.get(a[7]), out);
                break;
            }
            case "lanczos": {  // lanczos in.rgb w h x1 y1 x2 y2 dw dh out.rgb
                int w = Integer.parseInt(a[2]), h = Integer.parseInt(a[3]);
                int[] o = Lanczos.cropResize(readRgb(a[1], w, h), w, h,
                        Double.parseDouble(a[4]), Double.parseDouble(a[5]),
                        Double.parseDouble(a[6]), Double.parseDouble(a[7]),
                        Integer.parseInt(a[8]), Integer.parseInt(a[9]));
                writeRgb(a[10], o);
                break;
            }
            case "headtop": {  // headtop cases.csv  (png,fx1,fy1,fx2,fy2) -> satır başına sonuç
                try (BufferedReader r = new BufferedReader(new FileReader(a[1]))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] f = line.split(",");
                        BufferedImage im = ImageIO.read(new File(f[0]));
                        int w = im.getWidth(), h = im.getHeight();
                        int[] px = im.getRGB(0, 0, w, h, null, 0, w);
                        double[] face = {Double.parseDouble(f[1]), Double.parseDouble(f[2]),
                                Double.parseDouble(f[3]), Double.parseDouble(f[4])};
                        System.out.println(HeadTop.estimate(px, w, h, face));
                    }
                }
                break;
            }
            case "crop": {  // crop cases.csv (iw,ih,fx1,fy1,fx2,fy2|none,headTop|nan,topGap,headFrac)
                try (BufferedReader r = new BufferedReader(new FileReader(a[1]))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] f = line.split(",");
                        int iw = Integer.parseInt(f[0]), ih = Integer.parseInt(f[1]);
                        double[] face = f[2].equals("none") ? null : new double[]{Double.parseDouble(f[2]),
                                Double.parseDouble(f[3]), Double.parseDouble(f[4]), Double.parseDouble(f[5])};
                        double ht = f[6].equalsIgnoreCase("nan") ? Double.NaN : Double.parseDouble(f[6]);
                        Crop.Params p = new Crop.Params();
                        p.topGap = Double.parseDouble(f[7]);
                        p.headFrac = Double.parseDouble(f[8]);
                        double[] b = Crop.defaultCropBox(iw, ih, face, ht, p);
                        System.out.println(b[0] + "," + b[1] + "," + b[2] + "," + b[3]);
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException(a[0]);
        }
    }
}
