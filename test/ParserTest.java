import com.eokul.studio.core.*;
import com.eokul.studio.core.StudentListParser.Delim;
import com.eokul.studio.core.StudentListParser.Result;
import com.eokul.studio.core.StudentListParser.Row;

import java.util.List;

public class ParserTest {
    static int failures = 0;

    static void check(boolean cond, String msg) {
        if (!cond) {
            failures++;
            System.out.println("  BAŞARISIZ: " + msg);
        }
    }

    static void rows(Result r) {
        for (Row x : r.rows)
            System.out.println("    satır " + x.line + ": [" + x.no + "] [" + x.name + "]" + (x.ok() ? "" : "  ✗ " + x.error));
    }

    public static void main(String[] a) {
        // 1) Excel'den sekmeli kopya: Sıra No, Okul No, Adı, Soyadı, Cinsiyet, TC
        System.out.println("1) Excel sekmeli, çok sütunlu + başlık");
        String excel = "Sıra\tOkul No\tAdı\tSoyadı\tCinsiyeti\tT.C. Kimlik No\n"
                + "1\t1234\tAHMET\tYILMAZ\tE\t12345678901\n"
                + "2\t1235\tAYŞE NUR\tKAYA\tK\t10987654321\n"
                + "3\t1236\tİSMAİL\tÇELİK\tE\t11111111111\n";
        Result r = StudentListParser.parse(excel, Delim.AUTO, true);
        rows(r);
        check(r.delimiter == Delim.TAB && r.headerSkipped, "sekme + başlık atlanmalı");
        check(r.okCount() == 3 && r.errorCount() == 0, "3 geçerli satır");
        check(r.rows.get(0).no.equals("1234") && r.rows.get(0).name.equals("Ahmet Yılmaz"), "no=1234, Ahmet Yılmaz");
        check(r.rows.get(1).name.equals("Ayşe Nur Kaya"), "Ayşe Nur Kaya");
        check(r.rows.get(2).name.equals("İsmail Çelik"), "Türkçe büyük harf: İsmail Çelik, bulundu: " + r.rows.get(2).name);

        // 2) Noktalı virgül, iki sütun
        System.out.println("2) Noktalı virgül");
        r = StudentListParser.parse("101;Mehmet Demir\n102;Zeynep Şahin\n103;Ali Veli", Delim.AUTO, true);
        rows(r);
        check(r.delimiter == Delim.SEMICOLON && r.okCount() == 3, "3 geçerli, ;");

        // 3) Tire: "no-ad soyad" (e-Okul çıktı adı biçimi)
        System.out.println("3) Tire");
        r = StudentListParser.parse("2001-Ahmet Yılmaz\n2002 - Ayşe Kaya\n2003–Can Öz", Delim.AUTO, true);
        rows(r);
        check(r.delimiter == Delim.DASH && r.okCount() == 3, "3 geçerli, tire");
        check(r.rows.get(1).name.equals("Ayşe Kaya"), "boşluklu tire");

        // 4) Boşluk
        System.out.println("4) Boşluk");
        r = StudentListParser.parse("301 Burak Aydın\n302 Elif Su Tan\n303 Deniz Er", Delim.AUTO, true);
        rows(r);
        check(r.delimiter == Delim.SPACE && r.okCount() == 3, "3 geçerli, boşluk");
        check(r.rows.get(1).name.equals("Elif Su Tan"), "üç parçalı ad");

        // 5) Virgül + tırnaklı alan
        System.out.println("5) Virgül, tırnaklı");
        r = StudentListParser.parse("401,\"Kaya, Murat\"\n402,Selin Aksoy", Delim.AUTO, true);
        rows(r);
        check(r.okCount() == 2 && r.rows.get(0).name.equals("Kaya, Murat"), "tırnak içi virgül korunur");

        // 6) Hatalar: tekrar numara, ad yok, numara yok
        System.out.println("6) Hatalı satırlar");
        r = StudentListParser.parse("501;Ali Veli\n501;Veli Ali\n503;\n;Sadece Ad\n505;Cem Tan", Delim.AUTO, true);
        rows(r);
        check(r.okCount() == 2 && r.errorCount() == 3, "2 geçerli, 3 hatalı");
        check(r.rows.get(1).error != null && r.rows.get(1).error.contains("tekrar"), "tekrar numara");

        // 7) Elle ayraç seçimi ve harf düzeltme kapalı
        System.out.println("7) Elle ayraç, büyük harf düzeltmesi kapalı");
        r = StudentListParser.parse("601|AHMET YILMAZ", Delim.PIPE, false);
        rows(r);
        check(r.rows.get(0).name.equals("AHMET YILMAZ"), "büyük harf korunmalı");

        // 8) Sınıf ve cinsiyet sütunları ad sanılmamalı
        System.out.println("8) Sınıf/cinsiyet sütunları");
        r = StudentListParser.parse("12345\t9-A\tE\tOnur Kılıç\n12346\t9-A\tK\tPınar Güneş", Delim.AUTO, true);
        rows(r);
        check(r.okCount() == 2 && r.rows.get(0).name.equals("Onur Kılıç"), "yalnızca ad: Onur Kılıç");

        // 9) Boş / anlamsız giriş
        System.out.println("9) Boş giriş");
        r = StudentListParser.parse("   \n\n", Delim.AUTO, true);
        check(r.rows.isEmpty(), "boş giriş → satır yok");
        r = StudentListParser.parse("merhaba dünya\nbu bir liste değil", Delim.AUTO, true);
        rows(r);
        check(r.okCount() == 0, "anlamsız metin → geçerli satır yok");

        // 10) Windows satır sonu, BOM, NBSP
        System.out.println("10) CRLF + BOM + NBSP");
        r = StudentListParser.parse("﻿701;Ali Yıldız\r\n702;Ece Çınar\r\n", Delim.AUTO, true);
        rows(r);
        check(r.okCount() == 2 && r.rows.get(0).name.equals("Ali Yıldız"), "NBSP boşluğa çevrilir");

        // 11) Dosya adı üretimi
        System.out.println("11) Dosya adları");
        check(StudentListParser.fileBaseName("{no}-{ad}", "1234", "Ahmet Yılmaz", "9-A").equals("1234-Ahmet Yılmaz"), "1234-Ahmet Yılmaz");
        check(StudentListParser.fileBaseName("{no}", "1234", "Ahmet", "9-A").equals("1234"), "yalnız numara");
        check(StudentListParser.fileBaseName("{sinif}_{no}-{ad}", "7", "A/B:C*?", "9-A").equals("9-A_7-ABC"), "yasak karakterler atılır: " + StudentListParser.fileBaseName("{sinif}_{no}-{ad}", "7", "A/B:C*?", "9-A"));
        check(StudentListParser.sanitize("  ad.  ", 50).equals("ad"), "sondaki nokta/boşluk atılır");
        check(StudentListParser.sanitize("x".repeat(200), 60).length() == 60, "uzunluk sınırı");

        // 12) Kalite kuralları
        System.out.println("12) Kalite kuralları");
        Quality.Metrics m = new Quality.Metrics();
        m.faceCount = 1; m.sharpness = 200; m.luma = 120; m.cropWidth = 600;
        check(Quality.evaluate(m, 1, 133).isEmpty(), "iyi fotoğraf → uyarı yok");
        m.eyeOpen = 0.1; m.yaw = 30;
        List<String> w = Quality.evaluate(m, 1, 133);
        check(w.contains(Quality.EYES_CLOSED) && w.contains(Quality.HEAD_TURNED), "göz kapalı + dönük baş");
        check(!Quality.evaluate(m, 0, 133).contains(Quality.EYES_CLOSED) == false, "gevşek modda da 0.1 göz kapalı sayılır");
        m.eyeOpen = 0.25; m.yaw = 0;
        check(!Quality.evaluate(m, 0, 133).contains(Quality.EYES_CLOSED) && Quality.evaluate(m, 2, 133).contains(Quality.EYES_CLOSED), "hassasiyet 0 geçirir, 2 uyarır");
        m = new Quality.Metrics(); m.faceCount = 0;
        check(Quality.evaluate(m, 1, 133).equals(java.util.List.of(Quality.NO_FACE)), "yüz yok → yalnız NO_FACE");
        m = new Quality.Metrics(); m.faceCount = 2; m.sharpness = 5; m.luma = 20; m.cropWidth = 100; m.faceCut = true;
        w = Quality.evaluate(m, 1, 133);
        check(w.containsAll(List.of(Quality.MULTI_FACE, Quality.BLUR, Quality.DARK, Quality.SMALL, Quality.CUT)), "çoklu uyarı: " + w);

        System.out.println(failures == 0 ? "PARSER_OK" : ("BAŞARISIZ: " + failures));
        if (failures > 0) System.exit(1);
    }
}
