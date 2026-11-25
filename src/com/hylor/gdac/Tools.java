package com.hylor.gdac;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Conversores rapidos: base64, gzip, zlib. */
public class Tools {

    static final String[] NAMES = {
        "Base64: codificar texto",
        "Base64: decodificar texto",
        "Base64 URL-safe (GD): codificar texto",
        "GZIP + Base64 (nivel do GD): comprimir",
        "GZIP + Base64 (nivel do GD): descomprimir",
        "ZLIB + Base64: comprimir",
        "ZLIB + Base64: descomprimir"
    };

    static String run(int which, String in) throws Exception {
        byte[] raw = in.getBytes(StandardCharsets.UTF_8);
        switch (which) {
            case 0:
                return Base64.encodeToString(raw, Base64.NO_WRAP);
            case 1:
                return new String(decode(in.trim()), StandardCharsets.UTF_8);
            case 2:
                return Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_WRAP);
            case 3:
                return Codec.encodeLevel(in);
            case 4:
                return Codec.decodeLevel(in);
            case 5: {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                DeflaterOutputStream d = new DeflaterOutputStream(o);
                d.write(raw);
                d.close();
                return Base64.encodeToString(o.toByteArray(), Base64.URL_SAFE | Base64.NO_WRAP);
            }
            default: {
                byte[] z = decode(in.trim());
                return new String(Codec.readAll(new InflaterInputStream(new ByteArrayInputStream(z))), StandardCharsets.UTF_8);
            }
        }
    }

    static byte[] decode(String s) {
        try {
            return Base64.decode(s, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return Base64.decode(s, Base64.URL_SAFE);
        }
    }
}
