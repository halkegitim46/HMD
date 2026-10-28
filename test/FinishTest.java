import com.eokul.studio.core.*;
import java.io.*;
import java.nio.file.*;
public class FinishTest {
    // args: in.rgb w h x1 y1 x2 y2 outOld.rgb outNew.rgb
    public static void main(String[] a) throws Exception {
        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        int w = Integer.parseInt(a[1]), h = Integer.parseInt(a[2]);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | ((d[3*i]&255)<<16) | ((d[3*i+1]&255)<<8) | (d[3*i+2]&255);
        double[] box = new double[4];
        for (int i = 0; i < 4; i++) box[i] = Double.parseDouble(a[3 + i]);
        int[] old = Lanczos.cropResize(px, w, h, box[0], box[1], box[2], box[3], 133, 171);
        Finish.Opts o = new Finish.Opts();
        int[] nw = Finish.render(px, w, h, box, 133, 171, o);
        write(a[7], old); write(a[8], nw);
    }
    static void write(String f, int[] p) throws IOException {
        byte[] b = new byte[p.length * 3];
        for (int i = 0; i < p.length; i++) { b[3*i] = (byte)(p[i]>>16); b[3*i+1] = (byte)(p[i]>>8); b[3*i+2] = (byte)p[i]; }
        Files.write(Paths.get(f), b);
    }
}
