package com.gdarchive.compiler;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Executa o compilador ORIGINAL do SPWN (binario Rust compilado para Android como libspwn.so,
 * ver .github/workflows/build.yml). Nenhuma linguagem foi recriada: este app so chama o binario.
 */
public class SpwnRunner {
    public static File bin(Context c) { return new File(c.getApplicationInfo().nativeLibraryDir, "libspwn.so"); }
    public static boolean available(Context c) { return bin(c).exists(); }

    static void copyAssets(Context c, String from, File to) throws IOException {
        String[] list = c.getAssets().list(from);
        if (list == null || list.length == 0) {
            to.getParentFile().mkdirs();
            try (InputStream in = c.getAssets().open(from); OutputStream out = new FileOutputStream(to)) { out.write(Codec.readAll(in)); }
            return;
        }
        to.mkdirs();
        for (String s : list) copyAssets(c, from + "/" + s, new File(to, s));
    }

    public static String run(Context c, String code, String extraArgs) throws Exception {
        if (!available(c)) throw new IOException("libspwn.so ausente. Compile o SPWN original com o workflow do GitHub Actions (README).");
        File dir = new File(c.getFilesDir(), "spwn");
        dir.mkdirs();
        File libs = new File(dir, "libraries");
        if (!libs.exists()) { try { copyAssets(c, "spwn_libraries", libs); } catch (IOException ignored) { } }
        File script = new File(dir, "script.spwn");
        try (FileOutputStream o = new FileOutputStream(script)) { o.write(code.getBytes("UTF-8")); }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin(c).getAbsolutePath());
        cmd.add("build");
        cmd.add(script.getAbsolutePath());
        if (extraArgs != null) for (String a : extraArgs.trim().split("\\s+")) if (!a.isEmpty()) cmd.add(a);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true);
        pb.environment().put("HOME", c.getFilesDir().getAbsolutePath());
        Process p = pb.start();
        String out = new String(Codec.readAll(p.getInputStream()), "UTF-8");
        if (!p.waitFor(180, TimeUnit.SECONDS)) { p.destroyForcibly(); out += "\n[tempo esgotado]"; }
        return out + "\n[codigo de saida " + (p.isAlive() ? "?" : p.exitValue()) + "]";
    }

    /** Procura no texto de saida o level string (comeca com H4sI). */
    public static String findLevelString(String out) {
        for (String tok : out.split("\\s+")) if (tok.startsWith("H4sI") && tok.length() > 40) return tok;
        return null;
    }
}
