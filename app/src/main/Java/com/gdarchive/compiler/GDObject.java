package com.gdarchive.compiler;

import java.util.*;

/** Objeto do GD: pares chave,valor preservados como texto (campos desconhecidos nunca sao descartados). */
public class GDObject {
    public final LinkedHashMap<String, String> p = new LinkedHashMap<>();
    public String tail; // token sobrando se a contagem for impar

    public static GDObject parse(String s) {
        GDObject o = new GDObject();
        String[] t = s.split(",", -1);
        int i = 0;
        for (; i + 1 < t.length; i += 2) o.p.put(t[i], t[i + 1]);
        if (i < t.length && !t[i].isEmpty()) o.tail = t[i];
        return o;
    }

    public String serialize() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append(',').append(e.getValue());
        }
        if (tail != null) { if (b.length() > 0) b.append(','); b.append(tail); }
        return b.toString();
    }

    public float f(String k, float d) {
        String v = p.get(k);
        if (v == null) return d;
        try { return Float.parseFloat(v); } catch (Exception e) { return d; }
    }
    public int intv(String k, int d) { return (int) f(k, d); }
    public int id() { return intv("1", 0); }
    public float x() { return f("2", 0); }
    public float y() { return f("3", 0); }

    public static String fmt(float v) {
        if (v == (long) v) return Long.toString((long) v);
        return Float.toString(v);
    }
    public void set(String k, String v) { p.put(k, v); }
    public void setF(String k, float v) { p.put(k, fmt(v)); }

    public Set<Integer> groups() {
        Set<Integer> g = new LinkedHashSet<>();
        String v = p.get("57");
        if (v != null) for (String s : v.split("\\.")) try { g.add(Integer.parseInt(s.trim())); } catch (Exception ignored) { }
        return g;
    }

    public GDObject copy() {
        GDObject o = new GDObject();
        o.p.putAll(p);
        o.tail = tail;
        return o;
    }
}
