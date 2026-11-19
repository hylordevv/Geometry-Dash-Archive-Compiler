package com.gdarchive.compiler;

import org.json.*;
import java.util.*;

public class Decompiler {
    static final Map<String, String> SPWN_NAMES = new LinkedHashMap<>();
    static {
        String[] k = {"1", "OBJ_ID", "2", "X", "3", "Y", "6", "ROTATION", "32", "SCALING", "57", "GROUPS", "51", "TARGET", "10", "DURATION",
                "30", "EASING", "28", "MOVE_X", "29", "MOVE_Y", "35", "OPACITY"};
        for (int i = 0; i < k.length; i += 2) SPWN_NAMES.put(k[i], k[i + 1]);
    }

    /** Saida A: dados estruturados (JSON). Mantem todos os campos. */
    public static String structured(GDLevel l) throws JSONException {
        JSONObject r = new JSONObject();
        r.put("header", l.header);
        JSONArray a = new JSONArray();
        for (GDObject o : l.objects) {
            JSONObject j = new JSONObject();
            for (Map.Entry<String, String> e : o.p.entrySet()) j.put(e.getKey(), e.getValue());
            if (o.tail != null) j.put("__tail", o.tail);
            a.put(j);
        }
        r.put("objects", a);
        return r.toString(1);
    }

    /** Reverso: JSON estruturado -> nivel. */
    public static GDLevel fromStructured(String json, GDLevel base) throws JSONException {
        JSONObject r = new JSONObject(json);
        GDLevel l = new GDLevel();
        l.name = base.name;
        l.header = r.optString("header", base.header);
        JSONArray a = r.getJSONArray("objects");
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            GDObject o = new GDObject();
            Iterator<String> it = j.keys();
            while (it.hasNext()) { String k = it.next(); if (k.equals("__tail")) o.tail = j.getString(k); else o.p.put(k, j.get(k).toString()); }
            l.objects.add(o);
        }
        return l;
    }

    /** Saida B: reconstrucao ESTILO SPWN. NAO e o codigo-fonte original. */
    public static String spwnLike(GDLevel l) {
        StringBuilder b = new StringBuilder();
        b.append("// RECONSTRUCAO AUTOMATICA - nao e o codigo-fonte original.\n");
        b.append("// Nomes de propriedades seguem obj_props do SPWN; confira na sua versao.\n");
        b.append("// Campos sem nome conhecido aparecem como comentario /* chave=valor */ (nada e descartado).\n");
        b.append("extract obj_props\n\n");
        for (GDObject o : l.objects) {
            b.append("$.add(obj {");
            StringBuilder unk = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> e : o.p.entrySet()) {
                String n = SPWN_NAMES.get(e.getKey());
                if (n == null) { unk.append(' ').append(e.getKey()).append('=').append(e.getValue()); continue; }
                b.append(first ? " " : ", ").append(n).append(": ").append(value(e.getKey(), e.getValue()));
                first = false;
            }
            b.append(" })");
            if (unk.length() > 0) b.append(" /*").append(unk).append(" */");
            if (o.tail != null) b.append(" /* resto: ").append(o.tail).append(" */");
            b.append('\n');
        }
        return b.toString();
    }

    static String value(String k, String v) {
        if (k.equals("51")) return v + "g";
        if (k.equals("57")) return v.contains(".") ? "[" + v.replace(".", "g, ") + "g]" : v + "g";
        return v;
    }
}
