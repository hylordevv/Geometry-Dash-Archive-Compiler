package com.gdarchive.compiler;

import java.util.*;

/** Validacao de referencias, grafo de spawn e organizador automatico. */
public class Logic {
    static boolean isTrig(GDObject o) { return GDData.TRIGGERS.containsKey(o.id()); }

    static Map<Integer, Integer> groupCount(GDLevel l) {
        Map<Integer, Integer> m = new HashMap<>();
        for (GDObject o : l.objects) for (int g : o.groups()) m.put(g, m.getOrDefault(g, 0) + 1);
        return m;
    }

    public static List<String> validate(GDLevel l) {
        List<String> out = new ArrayList<>();
        Map<Integer, Integer> gc = groupCount(l);
        int n = 0;
        for (GDObject o : l.objects) {
            n++;
            if (!isTrig(o)) continue;
            int id = o.id();
            if (id != 899) {
                int t = o.intv("51", 0);
                if (t > 0 && !gc.containsKey(t))
                    out.add("QUEBRADA? " + GDData.name(id) + " em " + GDObject.fmt(o.x()) + "," + GDObject.fmt(o.y()) + " mira o grupo " + t + ", que nao tem objetos.");
                if (t == 0 && id != 1595 && id != 1912)
                    out.add("AVISO: " + GDData.name(id) + " em " + GDObject.fmt(o.x()) + "," + GDObject.fmt(o.y()) + " sem grupo alvo.");
            }
            if (id == 901 || id == 1346 || id == 2067 || id == 1347) {
                int r = o.intv("71", 0);
                if (r > 0 && !gc.containsKey(r))
                    out.add("QUEBRADA? " + GDData.name(id) + " usa grupo de referencia " + r + " sem objetos.");
            }
        }
        int dup = duplicates(l).size();
        if (dup > 0) out.add("INFO: " + dup + " objeto(s) duplicado(s) exatos.");
        if (out.isEmpty()) out.add("Nenhum problema encontrado em " + n + " objetos.");
        return out;
    }

    public static List<Integer> duplicates(GDLevel l) {
        Set<String> seen = new HashSet<>();
        List<Integer> d = new ArrayList<>();
        for (int i = 0; i < l.objects.size(); i++) if (!seen.add(l.objects.get(i).serialize())) d.add(i);
        return d;
    }

    /** Grafo de grupos: gatilho (grupos proprios) -> alvo; cadeias de Spawn em arvore. */
    public static String graph(GDLevel l) {
        StringBuilder b = new StringBuilder();
        Map<Integer, List<GDObject>> spawnFrom = new TreeMap<>();
        Set<Integer> targeted = new HashSet<>();
        List<GDObject> trigs = new ArrayList<>();
        for (GDObject o : l.objects) if (isTrig(o)) trigs.add(o);
        Collections.sort(trigs, (a, c) -> Float.compare(a.x(), c.x()));
        b.append("== Relacoes ==\n");
        int lines = 0;
        for (GDObject o : trigs) {
            if (lines++ > 400) { b.append("... (cortado)\n"); break; }
            String src = o.groups().isEmpty() ? "(sem grupo)" : "G" + o.groups();
            b.append(src).append(" -[").append(GDData.name(o.id())).append("]-> G").append(o.intv("51", 0)).append('\n');
            if (o.id() == 1268) {
                for (int g : o.groups()) { spawnFrom.computeIfAbsent(g, k -> new ArrayList<>()).add(o); }
                targeted.add(o.intv("51", 0));
            }
        }
        b.append("\n== Cadeias de Spawn ==\n");
        Set<Integer> roots = new TreeSet<>(spawnFrom.keySet());
        roots.removeAll(targeted);
        if (roots.isEmpty() && !spawnFrom.isEmpty()) roots.addAll(spawnFrom.keySet());
        for (int r : roots) walk(r, 0, spawnFrom, new HashSet<Integer>(), b);
        if (spawnFrom.isEmpty()) b.append("(nenhum Spawn Trigger)\n");
        return b.toString();
    }

    static void walk(int g, int depth, Map<Integer, List<GDObject>> m, Set<Integer> path, StringBuilder b) {
        for (int i = 0; i < depth; i++) b.append("  ");
        b.append("G").append(g);
        if (!path.add(g)) { b.append("  (CICLO)\n"); return; }
        b.append('\n');
        List<GDObject> next = m.get(g);
        if (next != null) for (GDObject s : next) {
            for (int i = 0; i <= depth; i++) b.append("  ");
            b.append("delay ").append(s.p.getOrDefault("63", "0")).append(" ->\n");
            walk(s.intv("51", 0), depth + 2, m, path, b);
        }
        path.remove(g);
    }

    /** Previa do organizador (nao altera o nivel). */
    public static String organizePreview(GDLevel l) {
        List<Integer> d = duplicates(l);
        StringBuilder b = new StringBuilder();
        b.append("Objetos: ").append(l.objects.size()).append("\nDuplicados exatos a remover: ").append(d.size()).append('\n');
        b.append("Reordenacao: por X, depois Y (gatilhos continuam executando por posicao X).\n");
        b.append("Nenhuma propriedade, grupo ou ID sera alterado.\n\n");
        for (String s : validate(l)) b.append("- ").append(s).append('\n');
        return b.toString();
    }

    public static void organizeApply(GDLevel l) {
        Set<String> seen = new HashSet<>();
        List<GDObject> out = new ArrayList<>();
        for (GDObject o : l.objects) if (seen.add(o.serialize())) out.add(o);
        Collections.sort(out, (a, c) -> {
            int r = Float.compare(a.x(), c.x());
            return r != 0 ? r : Float.compare(a.y(), c.y());
        });
        l.objects = out;
    }
}
