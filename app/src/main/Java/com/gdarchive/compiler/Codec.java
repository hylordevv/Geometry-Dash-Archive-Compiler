package com.gdarchive.compiler;

import android.util.Base64;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Serializacao do GD: Base64 (URL-safe) + gzip/zlib + texto "header;obj;obj;". */
public class Codec {
    static byte[] b64(String s) {
        s = s.replace('-', '+').replace('_', '/').replaceAll("\\s", "");
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == 0) end--;
        s = s.substring(0, end);
        while (s.length() % 4 != 0) s += "=";
        return Base64.decode(s, Base64.DEFAULT);
    }

    static byte[] gunzip(byte[] b) throws IOException {
        try (InputStream g = new GZIPInputStream(new ByteArrayInputStream(b))) { return readAll(g); }
    }
    static byte[] inflate(byte[] b, boolean raw) throws IOException {
        try (InputStream g = new InflaterInputStream(new ByteArrayInputStream(b), new Inflater(raw))) { return readAll(g); }
    }
    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    /** Detecta o formato e devolve o texto bruto "header;obj;...". */
    public static String decodeLevelData(String s) throws IOException {
        s = s.trim();
        if (s.indexOf(';') >= 0 && s.indexOf(',') >= 0 && !s.startsWith("H4sI")) return s; // ja e texto bruto
        byte[] d = b64(s);
        byte[] r;
        if (d.length > 2 && (d[0] & 0xFF) == 0x1F && (d[1] & 0xFF) == 0x8B) r = gunzip(d);
        else if (d.length > 2 && d[0] == 0x78) r = inflate(d, false);
        else r = inflate(d, true);
        return new String(r, "UTF-8");
    }

    public static String encode(String raw) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(o)) { g.write(raw.getBytes("UTF-8")); }
        return Base64.encodeToString(o.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    /** CCLocalLevels.dat (XOR 11 + Base64 + gzip -> plist XML) ou plist em XML puro. Retorna {nome, dadosK4}. */
    public static List<String[]> localLevels(byte[] data) throws Exception {
        String xml;
        String head = new String(data, 0, Math.min(5, data.length), "ISO-8859-1");
        if (head.startsWith("<?xml")) xml = new String(data, "UTF-8");
        else {
            byte[] x = data.clone();
            for (int i = 0; i < x.length; i++) x[i] ^= 11;
            try {
                xml = new String(gunzip(b64(new String(x, "ISO-8859-1"))), "UTF-8");
            } catch (Exception e) {
                throw new UnsupportedOperationException("Formato de CCLocalLevels nao suportado (XOR falhou; pode estar criptografado com AES).");
            }
        }
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("<k>k_\\d+</k>.*?<k>k2</k><s>(.*?)</s>.*?<k>k4</k><s>(.*?)</s>", Pattern.DOTALL).matcher(xml);
        while (m.find()) out.add(new String[]{unesc(m.group(1)), m.group(2)});
        return out;
    }

    static String unesc(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
