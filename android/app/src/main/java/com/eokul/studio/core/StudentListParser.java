package com.eokul.studio.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Excel / e-Okul'dan kopyalanıp yapıştırılan "numara + ad soyad" listesini ayrıştırır.
 *
 * Desteklenen ayraçlar: sekme (Excel), noktalı virgül, dikey çizgi, virgül, tire, boşluk.
 * Birden çok sütun varsa (Sıra No, Okul No, Adı, Soyadı, Cinsiyet, TC …) okul numarası ve ad sütunları
 * sezgisel olarak ayıklanır.
 */
public final class StudentListParser {
    private StudentListParser() {}

    public enum Delim { AUTO, TAB, SEMICOLON, PIPE, COMMA, DASH, SPACE }

    public static final class Row {
        public final int line;
        public final String no;
        public final String name;
        public final String error;

        Row(int line, String no, String name, String error) {
            this.line = line;
            this.no = no;
            this.name = name;
            this.error = error;
        }

        public boolean ok() { return error == null; }
    }

    public static final class Result {
        public final List<Row> rows = new ArrayList<>();
        public Delim delimiter = Delim.AUTO;
        public boolean headerSkipped = false;

        public int okCount() {
            int n = 0;
            for (Row r : rows) if (r.ok()) n++;
            return n;
        }

        public int errorCount() { return rows.size() - okCount(); }
    }

    private static final Pattern DIGITS = Pattern.compile("^\\d{1,12}$");
    private static final Pattern DASH_LINE = Pattern.compile("^\\s*(\\d+)\\s*[-\\u2013\\u2014]\\s*(\\S.*)$");
    private static final Pattern SPACE_LINE = Pattern.compile("^\\s*(\\d+)\\s+(\\S.*)$");
    private static final Pattern GENDER = Pattern.compile(
            "^(E|K|Erkek|K\\u0131z|Kiz|Bay|Bayan|Male|Female)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_LIKE = Pattern.compile(
            "^\\d{1,2}\\s*[-/.]?\\s*[A-Za-z\\u00c7\\u011e\\u0130\\u00d6\\u015e\\u00dc\\u00e7\\u011f\\u0131\\u00f6\\u015f\\u00fc]$");
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    // ------------------------------------------------------------------ ana giriş
    public static Result parse(String text, Delim requested, boolean fixCase) {
        Result res = new Result();
        List<String> lines = new ArrayList<>();
        List<Integer> lineNos = new ArrayList<>();
        if (text == null) text = "";
        String[] raw = text.replace("﻿", "").replace(' ', ' ').split("\\r\\n|\\n|\\r", -1);
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].trim().isEmpty()) continue;
            lines.add(raw[i]);
            lineNos.add(i + 1);
        }
        Delim d = (requested == Delim.AUTO) ? detect(lines) : requested;
        res.delimiter = d;
        if (lines.isEmpty()) return res;
        if (d == Delim.AUTO) {
            for (int i = 0; i < lines.size(); i++)
                res.rows.add(new Row(lineNos.get(i), "", "", "Ayraç tespit edilemedi"));
            return res;
        }

        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int ln = lineNos.get(i);
            String no = null, name = null, err = null;

            if (d == Delim.DASH || d == Delim.SPACE) {
                Matcher m = (d == Delim.DASH ? DASH_LINE : SPACE_LINE).matcher(line);
                if (m.matches()) {
                    no = m.group(1);
                    name = m.group(2);
                } else if (i == 0 && !startsWithDigit(line)) {
                    res.headerSkipped = true;
                    continue;
                } else {
                    err = "Satır biçimi anlaşılamadı";
                }
            } else {
                List<String> cells = splitCells(line, d);
                int best = -1;
                for (int c = 0; c < cells.size(); c++) {
                    String v = cells.get(c);
                    if (!DIGITS.matcher(v).matches()) continue;
                    if (best < 0) { best = c; continue; }
                    String cur = cells.get(best);
                    // TC kimlik no (11 hane) yerine okul numarası tercih edilir; ayrıca en uzun sayı kazanır.
                    boolean vTc = v.length() == 11, curTc = cur.length() == 11;
                    if (curTc && !vTc) best = c;
                    else if (!curTc && !vTc && v.length() > cur.length()) best = c;
                }
                if (best < 0) {
                    if (i == 0) {
                        res.headerSkipped = true;
                        continue;
                    }
                    err = "Numara bulunamadı";
                } else {
                    no = cells.get(best);
                    StringBuilder sb = new StringBuilder();
                    for (int c = 0; c < cells.size(); c++) {
                        if (c == best) continue;
                        String v = cells.get(c);
                        if (v.isEmpty() || DIGITS.matcher(v).matches() || !hasLetter(v)) continue;
                        if (GENDER.matcher(v).matches() || CLASS_LIKE.matcher(v).matches()) continue;
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(v);
                    }
                    name = sb.toString();
                }
            }

            if (err == null) {
                name = cleanName(name, fixCase);
                if (name.length() < 2 || !hasLetter(name)) err = "Ad soyad bulunamadı";
                else if (seen.containsKey(no)) err = "Aynı numara listede tekrar ediyor (satır " + seen.get(no) + ")";
                else seen.put(no, ln);
            }
            res.rows.add(new Row(ln, no == null ? "" : no, name == null ? "" : name, err));
        }
        return res;
    }

    // ------------------------------------------------------------------ ayraç tespiti
    static Delim detect(List<String> lines) {
        if (lines.isEmpty()) return Delim.AUTO;
        int n = lines.size();
        Delim[] order = {Delim.TAB, Delim.SEMICOLON, Delim.PIPE, Delim.COMMA};
        char[] chars = {'\t', ';', '|', ','};
        for (int k = 0; k < order.length; k++) {
            int c = 0;
            for (String l : lines) if (l.indexOf(chars[k]) >= 0) c++;
            if (c >= Math.ceil(n * 0.8)) return order[k];
        }
        int dash = 0, space = 0;
        for (String l : lines) {
            if (DASH_LINE.matcher(l).matches()) dash++;
            if (SPACE_LINE.matcher(l).matches()) space++;
        }
        // Başlık satırı olabileceğinden bir satır tolerans
        if (dash >= Math.max(1, n - 1) && dash >= space) return Delim.DASH;
        if (space >= Math.max(1, n - 1)) return Delim.SPACE;
        return Delim.AUTO;
    }

    private static boolean startsWithDigit(String s) {
        String t = s.trim();
        return !t.isEmpty() && Character.isDigit(t.charAt(0));
    }

    // ------------------------------------------------------------------ yardımcılar
    private static List<String> splitCells(String line, Delim d) {
        char sep = d == Delim.TAB ? '\t' : d == Delim.SEMICOLON ? ';' : d == Delim.PIPE ? '|' : ',';
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQ && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQ = !inQ;
                }
            } else if (ch == sep && !inQ) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    /** Boşlukları toplar, uçlardaki noktalama işaretlerini atar; hepsi büyük harfse "Ad Soyad" biçimine çevirir. */
    public static String cleanName(String s, boolean fixCase) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
        if (fixCase && !hasLowercase(t) && hasLetter(t)) t = titleCaseTr(t);
        return t;
    }

    private static boolean hasLowercase(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLowerCase(s.charAt(i))) return true;
        return false;
    }

    /** Türkçe büyük/küçük harf kurallarıyla ("İ", "ı") baş harfleri büyütür. */
    public static String titleCaseTr(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            if (Character.isLetter(s.charAt(i))) {
                sb.append(start ? ch.toUpperCase(TR) : ch.toLowerCase(TR));
                start = false;
            } else {
                sb.append(ch);
                start = ch.equals(" ") || ch.equals("-") || ch.equals("'") || ch.equals("’");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ dosya adları
    /** Dosya sistemi için güvenli ad: yasak karakterleri atar, boşlukları toplar, uzunluğu sınırlar. */
    public static String sanitize(String s, int max) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || "\\/:*?\"<>|".indexOf(c) >= 0) continue;
            sb.append(c);
        }
        String t = sb.toString().replaceAll("\\s+", " ").trim();
        t = t.replaceAll("[. ]+$", "");
        if (t.length() > max) t = t.substring(0, max).trim();
        return t;
    }

    /** "{no}-{ad}" gibi şablondan (uzantısız) dosya adı üretir. */
    public static String fileBaseName(String template, String no, String name, String className) {
        String t = (template == null || template.trim().isEmpty()) ? "{no}-{ad}" : template;
        String out = t.replace("{no}", no).replace("{ad}", name).replace("{sinif}", className == null ? "" : className);
        out = sanitize(out, 80);
        return out.isEmpty() ? sanitize(no, 80) : out;
    }
}
