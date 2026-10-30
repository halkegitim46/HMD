import com.eokul.studio.core.*;
import java.nio.file.*;
public class FinishTest2 {
  public static void main(String[] a) throws Exception {
    byte[] d=Files.readAllBytes(Paths.get("/tmp/noisy.rgb")); int w=1200,h=1600;
    int[] px=new int[w*h]; for(int i=0;i<px.length;i++)px[i]=0xFF000000|((d[3*i]&255)<<16)|((d[3*i+1]&255)<<8)|(d[3*i+2]&255);
    double[] box={150,150,1050,1370};
    for(int m=0;m<3;m++){Finish.Opts o=new Finish.Opts();o.denoise=m==1||m==2;o.sharpen=m==2?0.5:0;
      int[] r=Finish.render(px,w,h,box,133,171,o);
      byte[] b=new byte[r.length*3];for(int i=0;i<r.length;i++){b[3*i]=(byte)(r[i]>>16);b[3*i+1]=(byte)(r[i]>>8);b[3*i+2]=(byte)r[i];}
      Files.write(Paths.get("/tmp/m"+m+".rgb"),b);}
  }}
