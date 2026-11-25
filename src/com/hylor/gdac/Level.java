package com.hylor.gdac;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Modelo de um nivel do GD: cabecalho + lista de objetos (chave,valor). */
public class Level {

    static final String BLANK_HEADER =
            "kA13,0,kA15,0,kA16,0,kA14,,kA6,0,kA7,0,kA25,0,kA17,0,kA18,0,kS39,0,kA2,0,kA3,0,kA8,0,kA4,0,kA9,0,kA10,0,kA11,0";

    String header = BLANK_HEADER;
    ArrayList<GObj> objs = new ArrayList<GObj>();

    static class GObj {
        LinkedHashMap<String, String> p = new LinkedHashMap<String, String>();
        int id;
        float x, y, rot;
        boolean fx, fy;

        float num(String k, float d) {
            String v = p.get(k);
            if (v == null) {
                return d;
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return d;
            }
        }

        void sync() {
            id = (int) num("1", 0);
            x = num("2", 0);
            y = num("3", 0);
            rot = num("6", 0);
            fx = num("4", 0) != 0;
            fy = num("5", 0) != 0;
        }

        void put(String k, String v) {
            p.put(k, v);
            sync();
        }

        void remove(String k) {
            p.remove(k);
            sync();
        }

        GObj copy() {
            GObj o = new GObj();
            o.p.putAll(p);
            o.sync();
            return o;
        }

        String serialize() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> e : p.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(e.getKey()).append(',').append(e.getValue());
                first = false;
            }
            return sb.toString();
        }
    }

    static String fmt(float v) {
        if (v == (long) v) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    static GObj make(int id, float x, float y) {
        GObj o = new GObj();
        o.p.put("1", String.valueOf(id));
        o.p.put("2", fmt(x));
        o.p.put("3", fmt(y));
        o.sync();
        return o;
    }

    static Level blank() {
        return new Level();
    }

    static Level parse(String ls) {
        Level l = new Level();
        String[] parts = ls.split(";");
        l.header = parts.length > 0 ? parts[0] : "";
        for (int i = 1; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.length() == 0) {
                continue;
            }
            String[] kv = s.split(",", -1);
            GObj o = new GObj();
            for (int j = 0; j + 1 < kv.length; j += 2) {
                o.p.put(kv[j], kv[j + 1]);
            }
            o.sync();
            l.objs.add(o);
        }
        return l;
    }

    String toLevelString() {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(';');
        for (int i = 0; i < objs.size(); i++) {
            sb.append(objs.get(i).serialize()).append(';');
        }
        return sb.toString();
    }
}
