import com.eokul.studio.core.Quality;
import java.nio.file.*;
public class QualityProbe {
    public static void main(String[] a) throws Exception {
        int w = Integer.parseInt(a[1]), h = Integer.parseInt(a[2]);
        byte[] b = Files.readAllBytes(Paths.get(a[0]));
        int[] px = new int[w*h];
        for (int i = 0; i < w*h; i++) px[i] = 0xFF000000 | ((b[i*3]&0xFF)<<16) | ((b[i*3+1]&0xFF)<<8) | (b[i*3+2]&0xFF);
        double[] box = {Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6])};
        System.out.println(Quality.sharpness(px,w,h,box) + " " + Quality.meanLuma(px,w,h,box) + " " + Quality.clippedFraction(px,w,h,box));
    }
}
