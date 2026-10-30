import com.eokul.studio.core.*;
import java.nio.file.*;
public class SheetTest {
    public static void main(String[] a) throws Exception {
        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        String t = SheetReader.readText(d);
        System.out.print(t);
        System.out.println("-----");
        RosterParser.Result r = RosterParser.parse(t, RosterParser.Delimiter.AUTO, false, RosterParser.CaseMode.TITLE);
        System.out.println("detected=" + r.detected + " valid=" + r.valid().size() + " errors=" + r.errorCount() + " headers=" + r.skippedHeaders);
        for (RosterParser.Row x : r.rows) System.out.println(x.ok() ? x.no + " = " + x.name : "ERR " + x.line + " " + x.error);
    }
}
