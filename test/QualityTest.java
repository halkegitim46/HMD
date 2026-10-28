import com.eokul.studio.core.Quality;
import java.io.*;
import java.nio.file.*;
public class QualityTest {
    // args: file.rgb w h bx1 by1 bx2 by2
    public static void main(String[] a) throws Exception {
        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        int w = Integer.parseInt(a[1]), h = Integer.parseInt(a[2]);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | ((d[3*i]&255)<<16) | ((d[3*i+1]&255)<<8) | (d[3*i+2]&255);
        double[] box = {Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6])};
        Quality.Report r = Quality.analyze(px, w, h, box);
        System.out.printf("%.3f %.1f %.1f %b %b %b%n", r.blurRatio, r.brightness, r.contrast, r.blurry(), r.dark(), r.bright());
    }
}
