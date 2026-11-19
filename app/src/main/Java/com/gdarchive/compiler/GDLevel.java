package com.gdarchive.compiler;

import java.util.*;

public class GDLevel {
    public static final String DEFAULT_HEADER = "kS38,1_40_2_125_3_255_11_255_12_255_13_255_4_-1_6_1000_7_1_15_1_18_0_8_1|1_0_2_102_3_255_11_255_12_255_13_255_4_-1_6_1001_7_1_15_1_18_0_8_1|,kA13,0,kA15,0,kA16,0,kA14,,kA6,0,kA7,0,kA17,0,kA18,0,kS39,0,kA2,0,kA3,0,kA8,0,kA4,0,kA9,0,kA10,0,kA11,0";
    public String name = "Untitled";
    public String header = DEFAULT_HEADER;
    public List<GDObject> objects = new ArrayList<>();

    public static GDLevel fromRaw(String raw) {
        GDLevel l = new GDLevel();
        String[] seg = raw.split(";", -1);
        if (seg.length > 0) l.header = seg[0];
        for (int i = 1; i < seg.length; i++) if (!seg[i].isEmpty()) l.objects.add(GDObject.parse(seg[i]));
        return l;
    }

    public String toRaw() {
        StringBuilder b = new StringBuilder(header).append(';');
        for (GDObject o : objects) b.append(o.serialize()).append(';');
        return b.toString();
    }
}
