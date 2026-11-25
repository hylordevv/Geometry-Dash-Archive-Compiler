package com.hylor.gdac;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Leitura/escrita do formato de nivel do Geometry Dash (gzip + base64 + string de objetos). */
public class Codec {

    static final Map<String, String> NAMES = new HashMap<String, String>();
    static final Map<String, String> IDS = new HashMap<String, String>();
    static final Pattern NUM = Pattern.compile("-?\\d+(\\.\\d+)?");
    static final Pattern GRP = Pattern.compile("\\d+(\\.\\d+)*");
    static final Pattern OBJ = Pattern.compile("obj\\s*\\{([^}]*)\\}");

    static void def(String id, String name) {
        NAMES.put(id, name);
        IDS.put(name, id);
    }

    static {
        def("1", "OBJ_ID");
        def("2", "X");
        def("3", "Y");
        def("4", "HORIZONTAL_FLIP");
        def("5", "VERTICAL_FLIP");
        def("6", "ROTATION");
        def("7", "TRIGGER_RED");
        def("8", "TRIGGER_GREEN");
        def("9", "TRIGGER_BLUE");
        def("10", "DURATION");
        def("11", "TOUCH_TRIGGERED");
        def("20", "EDITOR_LAYER_1");
        def("21", "COLOR");
        def("22", "COLOR_2");
        def("23", "TARGET_COLOR");
        def("24", "Z_LAYER");
        def("25", "Z_ORDER");
        def("28", "MOVE_X");
        def("29", "MOVE_Y");
        def("30", "EASING");
        def("32", "SCALING");
        def("35", "OPACITY");
        def("51", "TARGET");
        def("57", "GROUPS");
        def("61", "EDITOR_LAYER_2");
    }

    // ---------- bytes ----------

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            o.write(buf, 0, n);
        }
        return o.toByteArray();
    }

    static byte[] inflateAny(byte[] d) throws IOException {
        try {
            return readAll(new GZIPInputStream(new ByteArrayInputStream(d)));
        } catch (IOException e) {
            return readAll(new InflaterInputStream(new ByteArrayInputStream(d)));
        }
    }

    static byte[] gzip(byte[] d) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        GZIPOutputStream g = new GZIPOutputStream(o);
        g.write(d);
        g.close();
        return o.toByteArray();
    }

    // ---------- nivel codificado ----------

    static String decodeLevel(String s) throws IOException {
        byte[] raw = Base64.decode(s.trim(), Base64.URL_SAFE);
        return new String(inflateAny(raw), StandardCharsets.UTF_8);
    }

    static String encodeLevel(String levelString) throws IOException {
        byte[] z = gzip(levelString.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(z, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    static boolean looksEncoded(String s) {
        String t = s.trim();
        return t.startsWith("H4sI") && t.indexOf('\n') < 0 && t.indexOf(' ') < 0;
    }

    static String extractK4(String xml) {
        Matcher m = Pattern.compile("<k>k4</k>\\s*<s>([^<]*)</s>").matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    static String xmlEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String wrapGmd(String name, String data) {
        return "<d><k>kCEK</k><i>4</i><k>k2</k><s>" + xmlEsc(name) + "</s><k>k3</k><s></s>"
                + "<k>k4</k><s>" + data + "</s><k>k5</k><s>GDAC</s><k>k13</k><t /><k>k21</k><i>2</i></d>";
    }

    /** Save do GD (CCLocalLevels.dat etc): XOR 11 -> base64 -> gzip. Retorna o XML ou null. */
    static String decryptSave(byte[] b) {
        try {
            byte[] x = new byte[b.length];
            for (int i = 0; i < b.length; i++) {
                x[i] = (byte) (b[i] ^ 11);
            }
            String s = new String(x, StandardCharsets.UTF_8).trim();
            if (!s.startsWith("H4sI")) {
                return null;
            }
            return decodeLevel(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Importacao: descobre o tipo do arquivo e devolve texto legivel. */
    static String loadBytes(byte[] b) throws IOException {
        String s = new String(b, StandardCharsets.UTF_8);
        String t = s.trim();
        if (t.contains("<k>k4</k>")) {
            String d = extractK4(t);
            if (d != null && d.length() > 0) {
                return decodeLevel(d);
            }
        }
        if (t.startsWith("H4sI")) {
            return decodeLevel(t);
        }
        String sv = decryptSave(b);
        if (sv != null) {
            return sv;
        }
        return s;
    }

    static String toLevelString(String text) throws IOException {
        String t = text.trim();
        if (t.contains("<k>k4</k>")) {
            String d = extractK4(t);
            if (d != null && d.length() > 0) {
                return decodeLevel(d);
            }
        }
        if (looksEncoded(t)) {
            return decodeLevel(t);
        }
        return t;
    }

    // ---------- importacao de qualquer formato ----------

    static class Entry {
        String name;
        String level;
    }

    static class Imported {
        List<Entry> levels = new ArrayList<Entry>();
        String text;
        String kind = "texto";
    }

    static final Pattern K2 = Pattern.compile("<k>k2</k>\\s*<s>([^<]*)</s>");
    static final Pattern K4 = Pattern.compile("<k>k4</k>\\s*<s>([^<]*)</s>");
    static final Pattern LVL = Pattern.compile("^k[A-Z]\\d+,");

    static String unxml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    /** Reconhece: .gmd, .gmd2 (zip), .lvl, saves .dat (XOR 11), gzip/zlib cru, nivel codificado, decodificado e codigo. */
    static Imported loadAny(byte[] b) {
        Imported r = new Imported();
        try {
            fill(b, r, 0);
        } catch (Exception e) {
            r.text = new String(b, StandardCharsets.UTF_8);
            r.kind = "texto";
        }
        return r;
    }

    static void fill(byte[] b, Imported r, int depth) throws IOException {
        if (depth > 3) {
            r.text = new String(b, StandardCharsets.UTF_8);
            return;
        }
        if (b.length > 4 && b[0] == 'P' && b[1] == 'K') {
            r.kind = "GMD2 / zip";
            ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(b));
            try {
                ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    if (ze.isDirectory()) {
                        continue;
                    }
                    Imported sub = new Imported();
                    try {
                        fill(readAll(zin), sub, depth + 1);
                    } catch (Exception ex) {
                        continue;
                    }
                    for (int i = 0; i < sub.levels.size(); i++) {
                        Entry en = sub.levels.get(i);
                        if (en.name == null || en.name.length() == 0) {
                            en.name = ze.getName();
                        }
                        r.levels.add(en);
                    }
                }
            } finally {
                zin.close();
            }
            if (r.levels.isEmpty()) {
                r.text = "(zip sem nivel reconhecido)";
            }
            return;
        }
        if (b.length > 2 && (b[0] & 0xFF) == 0x1f && (b[1] & 0xFF) == 0x8b) {
            r.kind = "gzip";
            fillText(new String(readAll(new GZIPInputStream(new ByteArrayInputStream(b))), StandardCharsets.UTF_8), r);
            return;
        }
        if (b.length > 2 && (b[0] & 0xFF) == 0x78) {
            int b1 = b[1] & 0xFF;
            if (b1 == 0x9c || b1 == 0xda || b1 == 0x01 || b1 == 0x5e) {
                try {
                    String z = new String(readAll(new InflaterInputStream(new ByteArrayInputStream(b))), StandardCharsets.UTF_8);
                    r.kind = "zlib";
                    fillText(z, r);
                    return;
                } catch (IOException e) {
                    // nao era zlib
                }
            }
        }
        String s = new String(b, StandardCharsets.UTF_8);
        String t = s.trim();
        char c0 = t.length() > 0 ? t.charAt(0) : ' ';
        if (c0 != '<' && c0 != 'H' && c0 != 'k' && c0 != '$' && c0 != '/' && c0 != '{') {
            String sv = decryptSave(b);
            if (sv != null) {
                r.kind = "save .dat";
                fillText(sv, r);
                return;
            }
        }
        fillText(s, r);
    }

    static void fillText(String s, Imported r) throws IOException {
        String t = s.trim();
        if (t.contains("<k>k4</k>")) {
            if (r.kind.equals("texto")) {
                r.kind = "GMD / LVL / save (plist)";
            }
            ArrayList<Integer> pos = new ArrayList<Integer>();
            ArrayList<String> names = new ArrayList<String>();
            Matcher m2 = K2.matcher(t);
            while (m2.find()) {
                pos.add(m2.start());
                names.add(unxml(m2.group(1)));
            }
            Matcher m4 = K4.matcher(t);
            int idx = -1;
            while (m4.find()) {
                while (idx + 1 < pos.size() && pos.get(idx + 1) < m4.start()) {
                    idx++;
                }
                Entry e = new Entry();
                e.name = idx >= 0 ? names.get(idx) : null;
                String data = m4.group(1);
                try {
                    e.level = data.startsWith("H4sI") ? decodeLevel(data) : unxml(data);
                    r.levels.add(e);
                } catch (Exception ex) {
                    // ignora nivel ilegivel
                }
            }
            if (r.levels.isEmpty()) {
                r.text = s;
            }
            return;
        }
        if (t.startsWith("H4sI") && t.indexOf(' ') < 0 && t.indexOf('\n') < 0) {
            Entry e = new Entry();
            e.level = decodeLevel(t);
            r.levels.add(e);
            if (r.kind.equals("texto")) {
                r.kind = "nivel codificado";
            }
            return;
        }
        if (LVL.matcher(t).find() && t.indexOf(';') > 0) {
            Entry e = new Entry();
            e.level = t;
            r.levels.add(e);
            if (r.kind.equals("texto")) {
                r.kind = "nivel decodificado";
            }
            return;
        }
        r.text = s;
    }

    // ---------- decompilar ----------

    static String esc(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String unesc(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' && i + 1 < v.length()) {
                sb.append(v.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String fmtVal(String k, String v) {
        if ("57".equals(k) && GRP.matcher(v).matches()) {
            return "[" + v.replace('.', ',') + "]";
        }
        if (NUM.matcher(v).matches()) {
            return v;
        }
        return "\"" + esc(v) + "\"";
    }

    static String decompile(String text) throws Exception {
        String ls = toLevelString(text);
        if (ls.startsWith("<")) {
            throw new Exception("Isso e um XML (save ou .gmd sem nivel). Importe um .gmd ou cole o texto do nivel.");
        }
        if (ls.indexOf(';') < 0) {
            throw new Exception("Isso nao parece um nivel do GD (nenhum ';' encontrado).");
        }
        String[] parts = ls.split(";");
        StringBuilder sb = new StringBuilder();
        sb.append("// GDAC - nivel decompilado\n");
        sb.append("header \"").append(esc(parts[0])).append("\"\n\n");
        int count = 0;
        for (int i = 1; i < parts.length; i++) {
            String o = parts[i].trim();
            if (o.length() == 0) {
                continue;
            }
            String[] kv = o.split(",");
            sb.append("$.add(obj{ ");
            for (int j = 0; j + 1 < kv.length; j += 2) {
                if (j > 0) {
                    sb.append(", ");
                }
                String k = kv[j];
                String name = NAMES.get(k);
                if (name == null) {
                    name = "P" + k;
                }
                sb.append(name).append(": ").append(fmtVal(k, kv[j + 1]));
            }
            sb.append(" })\n");
            count++;
        }
        sb.append("\n// ").append(count).append(" objetos\n");
        return sb.toString();
    }

    // ---------- compilar ----------

    static List<String> splitTop(String s) {
        List<String> r = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == '"') {
                    q = false;
                }
                continue;
            }
            if (c == '"') {
                q = true;
                cur.append(c);
            } else if (c == '[') {
                depth++;
                cur.append(c);
            } else if (c == ']') {
                depth--;
                cur.append(c);
            } else if (c == ',' && depth == 0) {
                r.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.toString().trim().length() > 0) {
            r.add(cur.toString());
        }
        return r;
    }

    static String parseValue(String v) {
        v = v.trim();
        if (v.startsWith("[") && v.endsWith("]")) {
            String inner = v.substring(1, v.length() - 1);
            StringBuilder sb = new StringBuilder();
            String[] items = inner.split(",");
            for (int i = 0; i < items.length; i++) {
                String it = items[i].trim();
                if (it.length() == 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(it);
            }
            return sb.toString();
        }
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return unesc(v.substring(1, v.length() - 1));
        }
        if ("true".equals(v)) {
            return "1";
        }
        if ("false".equals(v)) {
            return "0";
        }
        return v;
    }

    /** Codigo -> string de nivel (header;obj;obj;) */
    static String compileToLevelString(String code) throws Exception {
        String header = "";
        StringBuilder out = new StringBuilder();
        String[] lines = code.split("\n");
        int count = 0;
        for (int n = 0; n < lines.length; n++) {
            String l = lines[n].trim();
            if (l.length() == 0 || l.startsWith("//")) {
                continue;
            }
            if (l.startsWith("header")) {
                int a = l.indexOf('"');
                int b = l.lastIndexOf('"');
                if (a >= 0 && b > a) {
                    header = unesc(l.substring(a + 1, b));
                }
                continue;
            }
            Matcher m = OBJ.matcher(l);
            while (m.find()) {
                List<String> props = splitTop(m.group(1));
                StringBuilder ob = new StringBuilder();
                for (int i = 0; i < props.size(); i++) {
                    String p = props.get(i);
                    int c = p.indexOf(':');
                    if (c < 0) {
                        throw new Exception("Linha " + (n + 1) + ": faltou ':' em \"" + p.trim() + "\"");
                    }
                    String key = p.substring(0, c).trim();
                    String id;
                    if (IDS.containsKey(key)) {
                        id = IDS.get(key);
                    } else if (key.matches("P\\d+")) {
                        id = key.substring(1);
                    } else if (key.matches("\\d+")) {
                        id = key;
                    } else {
                        throw new Exception("Linha " + (n + 1) + ": propriedade desconhecida '" + key + "'");
                    }
                    if (ob.length() > 0) {
                        ob.append(',');
                    }
                    ob.append(id).append(',').append(parseValue(p.substring(c + 1)));
                }
                out.append(ob).append(';');
                count++;
            }
        }
        if (header.length() == 0 && count == 0) {
            throw new Exception("Nenhum header ou objeto encontrado no codigo.");
        }
        return header + ";" + out.toString();
    }

    static String compile(String code) throws Exception {
        return encodeLevel(compileToLevelString(code));
    }
}
