package com.hylor.gdac;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Codigo legivel do GDAC. Uma linha por objeto:
 *   Bloco pos(15,15)
 *   Trigger(Cor) pos(300,15) rgb(255,0,0) tempo(0.5) alvocor(1) zlayer(3) zorder(2)
 * Tambem aceita o formato SPWN antigo:  $.add(obj{ OBJ_ID: 1, X: 15, Y: 15 })
 */
public class Readable {

    static final String[][] NAMES = {
        {"1", "Bloco"}, {"40", "Laje"}, {"8", "Espinho"}, {"39", "EspinhoPequeno"},
        {"35", "PadAmarelo"}, {"67", "PadAzul"}, {"140", "PadRosa"},
        {"36", "OrbAmarelo"}, {"84", "OrbAzul"}, {"141", "OrbRosa"},
        {"10", "GravidadeNormal"}, {"11", "GravidadeInvertida"},
        {"12", "PortalCubo"}, {"13", "PortalNave"}, {"47", "PortalBola"}, {"111", "PortalUFO"},
        {"660", "PortalWave"}, {"745", "PortalRobo"}, {"1331", "PortalAranha"},
        {"200", "Velocidade05"}, {"201", "Velocidade1"}, {"202", "Velocidade2"},
        {"203", "Velocidade3"}, {"1334", "Velocidade4"},
        {"901", "Trigger(Mover)"}, {"899", "Trigger(Cor)"}, {"1007", "Trigger(Alpha)"},
        {"1049", "Trigger(Toggle)"}, {"1268", "Trigger(Spawn)"}, {"1346", "Trigger(Girar)"},
        {"1347", "Trigger(Seguir)"}, {"1006", "Trigger(Pulso)"}, {"1616", "Trigger(Parar)"}
    };

    static final String[][] ALIASES = {
        {"4", "flipx"}, {"5", "flipy"}, {"6", "rot"}, {"10", "tempo"}, {"11", "toque"},
        {"20", "camada1"}, {"61", "camada2"}, {"21", "cor"}, {"22", "cor2"}, {"23", "alvocor"},
        {"24", "zlayer"}, {"25", "zorder"}, {"30", "easing"}, {"32", "escala"},
        {"35", "opacidade"}, {"51", "alvo"}
    };

    static final Map<String, String> ID2NAME = new HashMap<String, String>();
    static final Map<String, String> NAME2ID = new HashMap<String, String>();
    static final Map<String, String> ID2ALIAS = new HashMap<String, String>();
    static final Map<String, String> ALIAS2ID = new HashMap<String, String>();

    static {
        for (int i = 0; i < NAMES.length; i++) {
            ID2NAME.put(NAMES[i][0], NAMES[i][1]);
            NAME2ID.put(norm(NAMES[i][1]), NAMES[i][0]);
        }
        for (int i = 0; i < ALIASES.length; i++) {
            ID2ALIAS.put(ALIASES[i][0], ALIASES[i][1]);
            ALIAS2ID.put(ALIASES[i][1], ALIASES[i][0]);
        }
    }

    static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().replace(" ", "").replace("_", "");
    }

    static String nameOf(int id) {
        String n = ID2NAME.get(String.valueOf(id));
        return n != null ? n : "Objeto(" + id + ")";
    }

    // ---------- nivel -> codigo ----------

    static String toCode(Level lv) {
        StringBuilder sb = new StringBuilder();
        sb.append("// GDAC - uma linha por objeto:  Nome(opcao) pos(x,y) zlayer(n) zorder(n) ...\n");
        sb.append("// Ex.:  Bloco pos(15,15)   |   Trigger(Cor) pos(300,15) rgb(255,0,0) tempo(0.5) alvocor(1) zlayer(3) zorder(2)\n");
        sb.append("// Objeto(ID) serve para qualquer ID sem nome; P<n>(valor) para propriedade sem nome.\n");
        sb.append("header \"").append(Codec.esc(lv.header)).append("\"\n\n");
        for (int i = 0; i < lv.objs.size(); i++) {
            appendObj(sb, lv.objs.get(i));
            sb.append('\n');
        }
        sb.append("\n// ").append(lv.objs.size()).append(" objetos\n");
        return sb.toString();
    }

    static String fmtArg(String v) {
        if (v.length() == 0 || v.indexOf('(') >= 0 || v.indexOf(')') >= 0 || v.indexOf(',') >= 0
                || v.indexOf('"') >= 0 || v.indexOf(' ') >= 0) {
            return "\"" + Codec.esc(v) + "\"";
        }
        return v;
    }

    static void appendObj(StringBuilder sb, Level.GObj o) {
        String idStr = o.p.get("1");
        String nm = idStr == null ? null : ID2NAME.get(idStr);
        sb.append(nm != null ? nm : "Objeto(" + (idStr == null ? "0" : idStr) + ")");
        String x = o.p.get("2");
        String y = o.p.get("3");
        boolean pos = x != null && y != null;
        if (pos) {
            sb.append(" pos(").append(x).append(',').append(y).append(')');
        }
        String r = o.p.get("7");
        String g = o.p.get("8");
        String b = o.p.get("9");
        boolean rgb = r != null && g != null && b != null;
        String mx = o.p.get("28");
        String my = o.p.get("29");
        boolean mv = mx != null && my != null;
        for (Map.Entry<String, String> e : o.p.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.equals("1")) {
                continue;
            }
            if (pos && (k.equals("2") || k.equals("3"))) {
                continue;
            }
            if (rgb && (k.equals("7") || k.equals("8") || k.equals("9"))) {
                if (k.equals("7")) {
                    sb.append(" rgb(").append(r).append(',').append(g).append(',').append(b).append(')');
                }
                continue;
            }
            if (mv && (k.equals("28") || k.equals("29"))) {
                if (k.equals("28")) {
                    sb.append(" mover(").append(mx).append(',').append(my).append(')');
                }
                continue;
            }
            if (k.equals("57")) {
                sb.append(" grupos(").append(v.replace('.', ',')).append(')');
                continue;
            }
            String al = ID2ALIAS.get(k);
            sb.append(' ').append(al != null ? al : "P" + k).append('(').append(fmtArg(v)).append(')');
        }
    }

    // ---------- codigo -> nivel ----------

    static class Tok {
        String name;
        String args;
    }

    static boolean idStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    static boolean idPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static List<Tok> tokenize(String line, int ln) throws Exception {
        List<Tok> r = new ArrayList<Tok>();
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t' || c == ',' || c == '-' || c == ';' || c == '|' || c == '\r') {
                i++;
                continue;
            }
            if (!idStart(c)) {
                throw new Exception("Linha " + ln + ": caractere inesperado '" + c + "'");
            }
            int s = i;
            while (i < n && idPart(line.charAt(i))) {
                i++;
            }
            Tok t = new Tok();
            t.name = line.substring(s, i);
            int j = i;
            while (j < n && line.charAt(j) == ' ') {
                j++;
            }
            if (j < n && line.charAt(j) == '(') {
                int k = j + 1;
                boolean q = false;
                while (k < n) {
                    char d = line.charAt(k);
                    if (q) {
                        if (d == '\\') {
                            k++;
                        } else if (d == '"') {
                            q = false;
                        }
                    } else if (d == '"') {
                        q = true;
                    } else if (d == ')') {
                        break;
                    }
                    k++;
                }
                if (k >= n) {
                    throw new Exception("Linha " + ln + ": faltou fechar ')' depois de " + t.name);
                }
                t.args = line.substring(j + 1, k);
                i = k + 1;
            }
            r.add(t);
        }
        return r;
    }

    static String unq(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return Codec.unesc(t.substring(1, t.length() - 1));
        }
        return t;
    }

    static List<String> args(String a) {
        List<String> out = new ArrayList<String>();
        if (a == null) {
            return out;
        }
        List<String> parts = Codec.splitTop(a);
        for (int i = 0; i < parts.size(); i++) {
            out.add(unq(parts.get(i)));
        }
        return out;
    }

    static String boolVal(String v) {
        String s = v.trim().toLowerCase();
        if (s.equals("sim") || s.equals("true") || s.equals("yes") || s.equals("1")) {
            return "1";
        }
        if (s.equals("nao") || s.equals("não") || s.equals("false") || s.equals("no") || s.equals("0")) {
            return "0";
        }
        return v;
    }

    static String need(List<String> a, int i, String mod, int ln) throws Exception {
        if (i >= a.size() || a.get(i).length() == 0) {
            throw new Exception("Linha " + ln + ": " + mod + "(...) precisa de mais valores");
        }
        return a.get(i);
    }

    static Level fromCode(String code) throws Exception {
        Level lv = new Level();
        String[] lines = code.split("\n");
        for (int n = 0; n < lines.length; n++) {
            String l = lines[n].trim();
            if (l.length() == 0 || l.startsWith("//") || l.startsWith("#")) {
                continue;
            }
            if (l.toLowerCase().startsWith("header")) {
                int a = l.indexOf('"');
                int b = l.lastIndexOf('"');
                if (a >= 0 && b > a) {
                    lv.header = Codec.unesc(l.substring(a + 1, b));
                }
                continue;
            }
            Matcher m = Codec.OBJ.matcher(l);
            if (m.find()) {
                try {
                    Level t = Level.parse(Codec.compileToLevelString(l));
                    lv.objs.addAll(t.objs);
                } catch (Exception e) {
                    throw new Exception("Linha " + (n + 1) + ": " + e.getMessage().replaceFirst("^Linha \\d+: ", ""));
                }
                continue;
            }
            lv.objs.add(parseObject(l, n + 1));
        }
        return lv;
    }

    static Level.GObj parseObject(String line, int ln) throws Exception {
        List<Tok> toks = tokenize(line, ln);
        if (toks.isEmpty()) {
            throw new Exception("Linha " + ln + ": linha vazia");
        }
        Tok first = toks.get(0);
        Level.GObj o = new Level.GObj();
        o.p.put("1", resolveId(first, ln));
        for (int i = 1; i < toks.size(); i++) {
            applyMod(o, toks.get(i), ln);
        }
        o.sync();
        return o;
    }

    static String resolveId(Tok first, int ln) throws Exception {
        String key = norm(first.name);
        if (first.args != null && first.args.trim().matches("\\d+")
                && (key.equals("objeto") || key.equals("obj") || key.equals("id") || key.equals("trigger"))) {
            return first.args.trim();
        }
        String full = first.args != null ? norm(first.name + "(" + first.args + ")") : key;
        String id = NAME2ID.get(full);
        if (id == null) {
            throw new Exception("Linha " + ln + ": objeto desconhecido '" + first.name
                    + (first.args != null ? "(" + first.args + ")" : "") + "'. Use Objeto(ID) para IDs numericos.");
        }
        return id;
    }

    static void applyMod(Level.GObj o, Tok t, int ln) throws Exception {
        String k = norm(t.name);
        List<String> a = args(t.args);
        if (k.equals("pos")) {
            o.p.put("2", need(a, 0, "pos", ln));
            o.p.put("3", need(a, 1, "pos", ln));
            if (a.size() >= 3) {
                o.p.put("24", a.get(2));
            }
        } else if (k.equals("rgb")) {
            o.p.put("7", need(a, 0, "rgb", ln));
            o.p.put("8", need(a, 1, "rgb", ln));
            o.p.put("9", need(a, 2, "rgb", ln));
        } else if (k.equals("mover")) {
            o.p.put("28", need(a, 0, "mover", ln));
            o.p.put("29", need(a, 1, "mover", ln));
        } else if (k.equals("grupos") || k.equals("grupo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).length() == 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(a.get(i));
            }
            o.p.put("57", sb.toString());
        } else if (ALIAS2ID.containsKey(k)) {
            String id = ALIAS2ID.get(k);
            String v = a.isEmpty() ? "1" : a.get(0);
            if (id.equals("4") || id.equals("5") || id.equals("11")) {
                v = boolVal(v);
            }
            o.p.put(id, v);
        } else if (k.matches("p\\d+")) {
            o.p.put(k.substring(1), a.isEmpty() ? "1" : unq(t.args));
        } else {
            throw new Exception("Linha " + ln + ": opcao desconhecida '" + t.name + "'");
        }
    }

    // ---------- SPWN ----------

    static String toSpwn(Level lv) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Exportado pelo GD Archive Compiler (so propriedades com nome conhecido)\n");
        int skipped = 0;
        for (int i = 0; i < lv.objs.size(); i++) {
            Level.GObj o = lv.objs.get(i);
            sb.append("$.add(obj{ ");
            boolean first = true;
            for (Map.Entry<String, String> e : o.p.entrySet()) {
                String nm = Codec.NAMES.get(e.getKey());
                if (nm == null) {
                    skipped++;
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                sb.append(nm).append(": ").append(Codec.fmtVal(e.getKey(), e.getValue()));
                first = false;
            }
            sb.append(" })\n");
        }
        if (skipped > 0) {
            sb.append("// ").append(skipped).append(" propriedades sem nome foram omitidas\n");
        }
        return sb.toString();
    }
}
