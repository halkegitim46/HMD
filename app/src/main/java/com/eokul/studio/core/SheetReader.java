package com.eokul.studio.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Harici kütüphane olmadan Excel dosyalarını okur: eski .xls (OLE2 + BIFF8) ve .xlsx (ZIP + XML).
 * İlk sayfayı satır satır metne çevirir (hücreler sekmeyle ayrılır); sonuç doğrudan {@link RosterParser}'a verilir.
 */
public final class SheetReader {
    private SheetReader() {}

    /** Dosya içeriğinden ilk sayfanın metnini üretir; desteklenmeyen biçimde IOException. */
    public static String readText(byte[] data) throws IOException {
        if (data.length > 50L * 1024 * 1024) throw new IOException("Dosya çok büyük (en fazla 50 MB)");
        List<String[]> rows;
        if (data.length > 4 && data[0] == 'P' && data[1] == 'K') {
            rows = readXlsx(data);
        } else if (data.length > 8 && (data[0] & 255) == 0xD0 && (data[1] & 255) == 0xCF) {
            rows = readXls(data);
        } else {
            throw new IOException("Excel dosyası değil");
        }
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) {
            StringBuilder line = new StringBuilder();
            boolean any = false;
            for (String c : r) {
                if (c == null || c.trim().isEmpty()) continue;
                if (any) line.append('\t');
                line.append(c.trim());
                any = true;
            }
            if (any) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** .xls uzantılı ama aslında HTML tablo olan dosyalar (bazı sistemlerin "Excel'e aktar" çıktısı). */
    public static boolean looksLikeHtml(String t) {
        String h = t.length() > 600 ? t.substring(0, 600) : t;
        h = h.toLowerCase(java.util.Locale.ROOT);
        return h.contains("<table") || h.contains("<html") || h.contains("<tr");
    }

    public static String htmlToText(String html) {
        String t = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        t = t.replaceAll("(?i)</t[dh]\\s*>", "\t").replaceAll("(?i)</tr\\s*>", "\n").replaceAll("(?i)<br\\s*/?>", " ");
        t = t.replaceAll("(?s)<[^>]*>", "");
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        t = unescape(t);
        StringBuilder sb = new StringBuilder();
        for (String line : t.split("\\r?\\n")) {
            String[] cells = line.split("\t", -1);
            StringBuilder l = new StringBuilder();
            for (int i = 0; i < cells.length; i++) { if (i > 0) l.append('\t'); l.append(cells[i].trim().replaceAll("\\s+", " ")); }
            if (l.toString().replace("\t", "").trim().length() > 0) sb.append(l).append('\n');
        }
        return sb.toString();
    }

    public static boolean looksLikeExcel(byte[] d) {
        return d.length > 4 && ((d[0] == 'P' && d[1] == 'K') || ((d[0] & 255) == 0xD0 && (d[1] & 255) == 0xCF));
    }

    // =================================================================== XLSX

    private static List<String[]> readXlsx(byte[] data) throws IOException {
        String shared = null, sheet = null, workbook = null, rels = null;
        Map<String, String> sheets = new TreeMap<>();
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > 2000) throw new IOException("Excel dosyası beklenenden fazla kayıt içeriyor");
                String n = e.getName();
                if (n.equals("xl/sharedStrings.xml")) shared = new String(readAll(zin), "UTF-8");
                else if (n.equals("xl/workbook.xml")) workbook = new String(readAll(zin), "UTF-8");
                else if (n.equals("xl/_rels/workbook.xml.rels")) rels = new String(readAll(zin), "UTF-8");
                else if (n.startsWith("xl/worksheets/") && n.endsWith(".xml") && n.indexOf('/', 14) < 0) sheets.put(n, new String(readAll(zin), "UTF-8"));
            }
        }
        if (sheets.isEmpty()) throw new IOException("Çalışma sayfası bulunamadı");
        // Sekme sırasındaki ilk sayfa: workbook.xml → ilişki kimliği → dosya yolu. Çözülemezse doğal sıralamayla ilk dosya.
        String first = firstSheetPath(workbook, rels);
        if (first != null && sheets.containsKey(first)) sheet = sheets.get(first);
        else {
            List<String> names = new ArrayList<>(sheets.keySet());
            java.util.Collections.sort(names, (x, y) -> {
                String a = x.replaceAll("\\D", ""), b = y.replaceAll("\\D", "");
                int ia = a.isEmpty() ? 0 : Integer.parseInt(a.length() > 9 ? a.substring(0, 9) : a);
                int ib = b.isEmpty() ? 0 : Integer.parseInt(b.length() > 9 ? b.substring(0, 9) : b);
                return ia != ib ? Integer.compare(ia, ib) : x.compareTo(y);
            });
            sheet = sheets.get(names.get(0));
        }

        List<String> sst = new ArrayList<>();
        if (shared != null) {
            Matcher si = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL).matcher(shared);
            Pattern t = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
            while (si.find()) {
                StringBuilder sb = new StringBuilder();
                Matcher tm = t.matcher(si.group(1));
                while (tm.find()) sb.append(unescape(tm.group(1)));
                sst.add(sb.toString());
            }
        }
        TreeMap<Integer, TreeMap<Integer, String>> grid = new TreeMap<>();
        Matcher cm = Pattern.compile("<c\\s([^>]*?)(?:/>|>(.*?)</c>)", Pattern.DOTALL).matcher(sheet);
        Pattern ref = Pattern.compile("r=\"([A-Z]+)(\\d+)\"");
        Pattern typ = Pattern.compile("t=\"(\\w+)\"");
        Pattern v = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL);
        Pattern isT = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
        while (cm.find()) {
            String attrs = cm.group(1), body = cm.group(2);
            if (body == null) continue;
            Matcher rm = ref.matcher(attrs);
            if (!rm.find()) continue;
            int col = colIndex(rm.group(1));
            int row = Integer.parseInt(rm.group(2));
            Matcher tm = typ.matcher(attrs);
            String type = tm.find() ? tm.group(1) : "n";
            String val = null;
            if (type.equals("inlineStr")) {
                StringBuilder sb = new StringBuilder();
                Matcher im = isT.matcher(body);
                while (im.find()) sb.append(unescape(im.group(1)));
                val = sb.toString();
            } else {
                Matcher vm = v.matcher(body);
                if (vm.find()) {
                    String raw = vm.group(1);
                    if (type.equals("s")) {
                        int idx = Integer.parseInt(raw.trim());
                        val = idx >= 0 && idx < sst.size() ? sst.get(idx) : "";
                    } else if (type.equals("str")) {
                        val = unescape(raw);
                    } else {
                        val = numText(raw.trim());
                    }
                }
            }
            if (val != null) grid.computeIfAbsent(row, k -> new TreeMap<>()).put(col, val);
        }
        return toRows(grid);
    }

    private static String firstSheetPath(String workbook, String rels) {
        if (workbook == null || rels == null) return null;
        Matcher sm = Pattern.compile("<sheet\\b[^>]*?r:id=\"([^\"]+)\"", Pattern.DOTALL).matcher(workbook);
        if (!sm.find()) return null;
        String rid = sm.group(1);
        Matcher rm = Pattern.compile("<Relationship\\b([^>]*)>", Pattern.DOTALL).matcher(rels);
        while (rm.find()) {
            String a = rm.group(1);
            Matcher id = Pattern.compile("\\bId=\"([^\"]+)\"").matcher(a);
            Matcher tg = Pattern.compile("\\bTarget=\"([^\"]+)\"").matcher(a);
            if (id.find() && id.group(1).equals(rid) && tg.find()) {
                String t = tg.group(1);
                return t.startsWith("/") ? t.substring(1) : "xl/" + t;
            }
        }
        return null;
    }

    private static int colIndex(String letters) {
        int n = 0;
        for (int i = 0; i < letters.length(); i++) n = n * 26 + (letters.charAt(i) - 'A' + 1);
        return n - 1;
    }

    private static final Pattern NUM_ENT = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");

    private static String unescape(String s) {
        if (s.indexOf("&#") >= 0) {
            Matcher m = NUM_ENT.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String g = m.group(1);
                int cp = g.charAt(0) == 'x' ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String numText(String raw) {
        try {
            double d = Double.parseDouble(raw);
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
            return raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static List<String[]> toRows(TreeMap<Integer, TreeMap<Integer, String>> grid) {
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<Integer, TreeMap<Integer, String>> r : grid.entrySet()) {
            TreeMap<Integer, String> cells = r.getValue();
            String[] arr = new String[cells.lastKey() + 1];
            for (Map.Entry<Integer, String> c : cells.entrySet()) arr[c.getKey()] = c.getValue();
            rows.add(arr);
        }
        return rows;
    }

    private static final long MAX_PART_BYTES = 64L * 1024 * 1024;

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_PART_BYTES) throw new IOException("Excel dosyasındaki bir bölüm beklenenden büyük");
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // =================================================================== XLS (OLE2 + BIFF8)

    private static int u16(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }
    private static int i32(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8) | ((b[o + 2] & 255) << 16) | ((b[o + 3] & 255) << 24); }

    private static byte[] oleStream(byte[] f, String wantA, String wantB) throws IOException {
        int shift = u16(f, 0x1E), sec = 1 << shift;
        int miniShift = u16(f, 0x20), miniSec = 1 << miniShift;
        int nFat = i32(f, 0x2C), dirStart = i32(f, 0x30), cutoff = i32(f, 0x38);
        int miniFatStart = i32(f, 0x3C), nMiniFat = i32(f, 0x40), difStart = i32(f, 0x44), nDif = i32(f, 0x48);
        List<Integer> fatSecs = new ArrayList<>();
        for (int i = 0; i < 109 && fatSecs.size() < nFat; i++) fatSecs.add(i32(f, 0x4C + 4 * i));
        int dif = difStart;
        for (int d = 0; d < nDif && dif >= 0; d++) {
            int off = (dif + 1) * sec;
            for (int i = 0; i < sec / 4 - 1 && fatSecs.size() < nFat; i++) fatSecs.add(i32(f, off + 4 * i));
            dif = i32(f, off + sec - 4);
        }
        int[] fat = new int[fatSecs.size() * (sec / 4)];
        for (int i = 0; i < fatSecs.size(); i++) {
            int off = (fatSecs.get(i) + 1) * sec;
            for (int k = 0; k < sec / 4; k++) fat[i * (sec / 4) + k] = i32(f, off + 4 * k);
        }
        byte[] dir = chain(f, fat, dirStart, sec, -1);
        int rootStart = -1;
        List<Object[]> streams = new ArrayList<>();   // {ad, başlangıç, boyut}
        for (int p = 0; p + 128 <= dir.length; p += 128) {
            int nameLen = u16(dir, p + 0x40);
            if (nameLen < 2 || nameLen > 64) continue;
            String name = new String(dir, p, nameLen - 2, "UTF-16LE");
            int type = dir[p + 0x42] & 255;
            int start = i32(dir, p + 0x74), size = i32(dir, p + 0x78);
            if (type == 5) rootStart = start;
            if (type == 2) streams.add(new Object[]{name, start, size});
        }
        // Aday sırası: bilinen adlar (büyük/küçük harf fark etmez), sonra diğer tüm akışlar. Her aday BIFF "BOF" kaydıyla başlamalı.
        List<Object[]> order = new ArrayList<>();
        for (Object[] st : streams) {
            String n = (String) st[0];
            if (n.equalsIgnoreCase(wantA) || n.equalsIgnoreCase(wantB)) order.add(st);
        }
        for (Object[] st : streams) if (!order.contains(st)) order.add(st);
        for (Object[] st : order) {
            byte[] data;
            try {
                data = extract(f, fat, sec, cutoff, rootStart, miniFatStart, nMiniFat, miniSec, (Integer) st[1], (Integer) st[2]);
            } catch (RuntimeException e) {
                continue;
            }
            if (data.length >= 4 && (data[1] & 255) == 0x08 && ((data[0] & 255) == 0x09)) return data;   // BIFF8 BOF (0x0809)
            if (data.length >= 4 && (data[1] & 255) <= 0x08 && (data[0] & 255) == 0x09) return data;    // eski BIFF (0x0009/0x0209/0x0409)
        }
        StringBuilder names = new StringBuilder();
        boolean encrypted = false;
        for (Object[] st : streams) {
            String n = (String) st[0];
            if (n.equalsIgnoreCase("EncryptedPackage") || n.equalsIgnoreCase("EncryptionInfo")) encrypted = true;
            if (names.length() > 0) names.append(", ");
            names.append(n.replaceAll("[\\p{Cntrl}]", "?"));
        }
        if (encrypted) throw new IOException("Excel dosyası parolalı/şifreli; parolasız kaydedip tekrar deneyin");
        throw new IOException("Workbook akışı bulunamadı (dosyadaki akışlar: " + (names.length() == 0 ? "yok" : names) + ")");
    }

    private static byte[] extract(byte[] f, int[] fat, int sec, int cutoff, int rootStart, int miniFatStart, int nMiniFat,
                                  int miniSec, int wStart, int wSize) throws IOException {
        if (wStart < 0 || wSize < 0) return new byte[0];
        if (wSize >= cutoff) return chain(f, fat, wStart, sec, wSize);
        byte[] mini = chain(f, fat, rootStart, sec, -1);
        byte[] miniFatBytes = chain(f, fat, miniFatStart, sec, -1);
        int[] miniFat = new int[miniFatBytes.length / 4];
        for (int i = 0; i < miniFat.length; i++) miniFat[i] = i32(miniFatBytes, 4 * i);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int s = wStart, guard = 0;
        while (s >= 0 && s < miniFat.length && guard++ < 1_000_000) {
            int off = s * miniSec;
            if (off + miniSec > mini.length) break;
            out.write(mini, off, miniSec);
            s = miniFat[s];
        }
        byte[] all = out.toByteArray();
        return java.util.Arrays.copyOf(all, Math.min(wSize, all.length));
    }

    private static byte[] chain(byte[] f, int[] fat, int start, int sec, int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int s = start, guard = 0;
        while (s >= 0 && s < fat.length && guard++ < 5_000_000) {
            int off = (s + 1) * sec;
            if (off + sec > f.length) break;
            out.write(f, off, sec);
            s = fat[s];
        }
        byte[] all = out.toByteArray();
        return size >= 0 ? java.util.Arrays.copyOf(all, Math.min(size, all.length)) : all;
    }

    /** SST'deki bölünmüş (CONTINUE) kayıtlar üzerinde dize okuyucu. */
    private static final class Seg {
        final List<byte[]> parts = new ArrayList<>();
        int pi = 0, po = 0;

        boolean has() { return pi < parts.size(); }

        int left() { return parts.get(pi).length - po; }

        int u8() throws IOException {
            if (po >= parts.get(pi).length) {
                pi++;
                po = 0;
                if (pi >= parts.size()) throw new IOException("SST bitti");
            }
            return parts.get(pi)[po++] & 255;
        }

        int u16() throws IOException { int a = u8(); return a | (u8() << 8); }

        int i32() throws IOException { int a = u16(); return a | (u16() << 16); }

        void skip(int n) throws IOException { for (int i = 0; i < n; i++) u8(); }
    }

    private static String sstString(Seg s) throws IOException {
        int cch = s.u16();
        int flags = s.u8();
        int runs = 0, ext = 0;
        if ((flags & 8) != 0) runs = s.u16();
        if ((flags & 4) != 0) ext = s.i32();
        boolean wide = (flags & 1) != 0;
        StringBuilder sb = new StringBuilder(cch);
        int remaining = cch;
        while (remaining > 0) {
            if (s.left() == 0) {            // yeni CONTINUE: ilk bayt karakter biçimi bayrağı
                s.pi++;
                s.po = 0;
                if (s.pi >= s.parts.size()) throw new IOException("SST bitti");
                wide = (s.u8() & 1) != 0;
            }
            int perChar = wide ? 2 : 1;
            int fit = Math.min(remaining, s.left() / perChar);
            if (fit == 0) {                 // tek bir geniş karakterin yarısı kaldıysa
                fit = 1;
            }
            for (int i = 0; i < fit; i++) {
                if (wide) sb.append((char) s.u16()); else sb.append((char) s.u8());
            }
            remaining -= fit;
        }
        s.skip(runs * 4);
        s.skip(ext);
        return sb.toString();
    }

    private static void put(TreeMap<Integer, TreeMap<Integer, String>> g, int row, int col, String v) {
        g.computeIfAbsent(row, k -> new TreeMap<>()).put(col, v);
    }

    private static String rk(int v) {
        double d;
        if ((v & 2) != 0) {
            d = v >> 2;
        } else {
            d = Double.longBitsToDouble(((long) (v & 0xFFFFFFFC)) << 32);
        }
        if ((v & 1) != 0) d /= 100.0;
        return numText(Double.toString(d));
    }

    private static List<String[]> readXls(byte[] file) throws IOException {
        byte[] wb = oleStream(file, "Workbook", "Book");
        List<String> sst = new ArrayList<>();
        TreeMap<Integer, TreeMap<Integer, String>> grid = new TreeMap<>();
        int pos = 0;
        int sheetsSeen = 0;
        boolean inFirstSheet = false;
        while (pos + 4 <= wb.length) {
            int id = u16(wb, pos), len = u16(wb, pos + 2);
            int d = pos + 4;
            if (d + len > wb.length) break;
            if (id == 0x0809) {                       // BOF
                if (u16(wb, d + 2) == 0x10) {
                    sheetsSeen++;
                    inFirstSheet = sheetsSeen == 1;
                }
            } else if (id == 0x000A) {                // EOF
                if (inFirstSheet) break;
            } else if (id == 0x00FC) {                // SST
                Seg seg = new Seg();
                seg.parts.add(java.util.Arrays.copyOfRange(wb, d, d + len));
                int q = d + len;
                while (q + 4 <= wb.length && u16(wb, q) == 0x003C) {
                    int l2 = u16(wb, q + 2);
                    seg.parts.add(java.util.Arrays.copyOfRange(wb, q + 4, q + 4 + l2));
                    q += 4 + l2;
                }
                seg.i32();                            // toplam başvuru sayısı
                int unique = seg.i32();
                try {
                    for (int i = 0; i < unique; i++) sst.add(sstString(seg));
                } catch (IOException e) {
                    // kesik SST: okunanlarla devam
                }
            } else if (inFirstSheet) {
                if (id == 0x00FD && len >= 10) {      // LABELSST
                    int idx = i32(wb, d + 6);
                    put(grid, u16(wb, d), u16(wb, d + 2), idx >= 0 && idx < sst.size() ? sst.get(idx) : "");
                } else if (id == 0x0204 && len >= 8) { // LABEL
                    int cch = u16(wb, d + 6);
                    int flags = wb[d + 8] & 255;
                    String str;
                    if ((flags & 1) != 0) str = new String(wb, d + 9, Math.min(cch * 2, len - 9), "UTF-16LE");
                    else str = new String(wb, d + 9, Math.min(cch, len - 9), "ISO-8859-1");
                    put(grid, u16(wb, d), u16(wb, d + 2), str);
                } else if (id == 0x0203 && len >= 14) { // NUMBER
                    long bits = 0;
                    for (int k = 7; k >= 0; k--) bits = (bits << 8) | (wb[d + 6 + k] & 255);
                    put(grid, u16(wb, d), u16(wb, d + 2), numText(Double.toString(Double.longBitsToDouble(bits))));
                } else if (id == 0x027E && len >= 10) { // RK
                    put(grid, u16(wb, d), u16(wb, d + 2), rk(i32(wb, d + 6)));
                } else if (id == 0x00BD && len >= 6) { // MULRK
                    int row = u16(wb, d), first = u16(wb, d + 2);
                    int n = (len - 6) / 6;
                    for (int k = 0; k < n; k++) put(grid, row, first + k, rk(i32(wb, d + 4 + 6 * k + 2)));
                }
            }
            pos = d + len;
        }
        return toRows(grid);
    }
}
