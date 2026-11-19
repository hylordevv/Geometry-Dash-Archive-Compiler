package com.gdarchive.compiler;

import java.util.*;

/** Tabelas de IDs/chaves conhecidas. Confira contra a versao do jogo; chaves desconhecidas sao preservadas. */
public class GDData {
    public static final Map<Integer, String> TRIGGERS = new LinkedHashMap<>();
    public static final Map<String, String> KEYS = new LinkedHashMap<>();
    public static final String[] EASING = {"None", "Ease In Out", "Ease In", "Ease Out", "Elastic In Out", "Elastic In", "Elastic Out",
            "Bounce In Out", "Bounce In", "Bounce Out", "Exp In Out", "Exp In", "Exp Out", "Sine In Out", "Sine In", "Sine Out",
            "Back In Out", "Back In", "Back Out"};
    public static final Set<Integer> SPIKES = new HashSet<>(Arrays.asList(8, 9, 39, 103, 392));
    public static final Set<Integer> ORBS = new HashSet<>(Arrays.asList(36, 84, 141, 1022, 1330, 1333));
    public static final Set<Integer> PADS = new HashSet<>(Arrays.asList(35, 67, 140, 1332));
    public static final Set<Integer> PORTALS = new HashSet<>(Arrays.asList(10, 11, 12, 13, 47, 99, 101, 111, 286, 287, 660, 745, 1331));
    /** Categorias da paleta: nome -> {id, rotulo}. */
    public static final LinkedHashMap<String, int[]> PALETTE = new LinkedHashMap<>();

    static {
        TRIGGERS.put(901, "Move"); TRIGGERS.put(1346, "Rotate"); TRIGGERS.put(2067, "Scale"); TRIGGERS.put(1007, "Alpha");
        TRIGGERS.put(1049, "Toggle"); TRIGGERS.put(1268, "Spawn"); TRIGGERS.put(1347, "Follow"); TRIGGERS.put(899, "Color");
        TRIGGERS.put(1006, "Pulse"); TRIGGERS.put(1595, "Touch"); TRIGGERS.put(1611, "Count"); TRIGGERS.put(1616, "Stop");
        TRIGGERS.put(1912, "Random");
        String[] k = {"1", "ID", "2", "X", "3", "Y", "6", "Rotacao", "10", "Duracao", "20", "Layer editor", "21", "Cor 1", "22", "Cor 2",
                "24", "Z layer", "25", "Z order", "28", "Move X", "29", "Move Y", "30", "Easing", "32", "Escala", "35", "Opacidade",
                "51", "Grupo alvo", "56", "Ativar grupo", "57", "Grupos", "63", "Delay spawn", "68", "Graus", "71", "Grupo centro/ref",
                "7", "R", "8", "G", "9", "B", "23", "Canal de cor alvo", "150", "Escala X", "151", "Escala Y"};
        for (int i = 0; i < k.length; i += 2) KEYS.put(k[i], k[i + 1]);
        PALETTE.put("Blocos", new int[]{1, 2, 3, 4, 5, 6, 7, 40, 83});
        PALETTE.put("Espinhos", new int[]{8, 39, 103});
        PALETTE.put("Portais", new int[]{10, 11, 12, 13, 47, 111, 660, 745, 1331});
        PALETTE.put("Orbs/Pads", new int[]{36, 84, 141, 1022, 1333, 1330, 35, 67, 140, 1332});
        int[] t = new int[TRIGGERS.size()];
        int i = 0;
        for (int id : TRIGGERS.keySet()) t[i++] = id;
        PALETTE.put("Triggers", t);
    }

    public static String name(int id) {
        if (TRIGGERS.containsKey(id)) return TRIGGERS.get(id) + " Trigger";
        if (SPIKES.contains(id)) return "Spike";
        if (ORBS.contains(id)) return "Orb";
        if (PADS.contains(id)) return "Pad";
        if (PORTALS.contains(id)) return "Portal";
        return "Obj";
    }

    public static String describe(GDObject o) {
        int id = o.id();
        StringBuilder b = new StringBuilder();
        b.append(name(id)).append(" #").append(id).append(" @").append(GDObject.fmt(o.x())).append(',').append(GDObject.fmt(o.y()));
        if (o.p.containsKey("57")) b.append("  grupos=").append(o.p.get("57"));
        if (o.p.containsKey("51")) b.append("  alvo=").append(o.p.get("51"));
        if (o.p.containsKey("10")) b.append("  dur=").append(o.p.get("10"));
        int e = o.intv("30", 0);
        if (o.p.containsKey("30")) b.append("  ease=").append(e >= 0 && e < EASING.length ? EASING[e] : "?" + e);
        switch (id) {
            case 901: b.append("  dx=").append(o.p.getOrDefault("28", "0")).append(" dy=").append(o.p.getOrDefault("29", "0")); break;
            case 1346: b.append("  graus=").append(o.p.getOrDefault("68", "0")).append(" centro=").append(o.p.getOrDefault("71", "0")); break;
            case 2067: b.append("  sx=").append(o.p.getOrDefault("150", "1")).append(" sy=").append(o.p.getOrDefault("151", "1")); break;
            case 1007: b.append("  opac=").append(o.p.getOrDefault("35", "1")); break;
            case 1049: b.append("  ativar=").append(o.p.getOrDefault("56", "0")); break;
            case 1268: b.append("  delay=").append(o.p.getOrDefault("63", "0")); break;
            case 1347: b.append("  segue=").append(o.p.getOrDefault("71", "0")); break;
            case 899: b.append("  rgb=").append(o.p.getOrDefault("7", "0")).append('/').append(o.p.getOrDefault("8", "0")).append('/').append(o.p.getOrDefault("9", "0"))
                    .append(" canal=").append(o.p.getOrDefault("23", "0")); break;
            default: break;
        }
        return b.toString();
    }
}
