import com.eokul.studio.core.*;
public class HtmlTest { public static void main(String[] a){
 String h="<html><body><table><tr><th>S.No</th><th>Ad&#252; Soyad</th></tr><tr><td>12345</td><td>Ay&#351;e&nbsp;Y&#305;lmaz</td></tr><tr><td>12346</td><td>Mehmet Kaya</td></tr></table></body></html>";
 System.out.println(SheetReader.looksLikeHtml(h)); String t=SheetReader.htmlToText(h); System.out.print(t);
 System.out.println(RosterParser.parse(t, RosterParser.Delimiter.AUTO,false,RosterParser.CaseMode.KEEP).valid().size());}}
