package com.eokul.studio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import org.junit.Test;

public class SheetReaderTest {
    @Test
    public void xlsFileGivesStudentsAndIgnoresFooter() throws Exception {
        byte[] data;
        try (InputStream in = getClass().getResourceAsStream("/ornek_liste.xls")) {
            data = in.readAllBytes();
        }
        assertTrue(SheetReader.looksLikeExcel(data));
        String text = SheetReader.readText(data);
        RosterParser.Result r = RosterParser.parse(text, RosterParser.Delimiter.AUTO, false, RosterParser.CaseMode.TITLE);
        assertEquals(8, r.valid().size());
    }

    @Test
    public void htmlTableXlsIsParsed() {
        String h = "<html><body><table><tr><th>S.No</th><th>Ad Soyad</th></tr>"
                + "<tr><td>12345</td><td>Ay&#351;e&nbsp;Y&#305;lmaz</td></tr>"
                + "<tr><td>12346</td><td>Mehmet Kaya</td></tr></table></body></html>";
        assertTrue(SheetReader.looksLikeHtml(h));
        RosterParser.Result r = RosterParser.parse(SheetReader.htmlToText(h), RosterParser.Delimiter.AUTO, false, RosterParser.CaseMode.KEEP);
        assertEquals(2, r.valid().size());
        assertEquals("Ayşe Yılmaz", r.valid().get(0).name);
    }
}
