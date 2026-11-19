package com.gdarchive.compiler;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;

/** Ajuda com IA (opcional, precisa de internet e chave de API propria). O resto do app funciona offline. */
public class Ai {
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gdac", Context.MODE_PRIVATE); }

    public static String ask(Context c, String system, String user) throws Exception {
        String key = prefs(c).getString("ai_key", "");
        if (key.isEmpty()) throw new IOException("Defina a chave de API em Configuracoes.");
        JSONObject body = new JSONObject();
        body.put("model", prefs(c).getString("ai_model", "claude-sonnet-5-5"));
        body.put("max_tokens", 1500);
        body.put("system", system);
        body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
        h.setRequestMethod("POST");
        h.setConnectTimeout(20000);
        h.setReadTimeout(90000);
        h.setDoOutput(true);
        h.setRequestProperty("content-type", "application/json");
        h.setRequestProperty("x-api-key", key);
        h.setRequestProperty("anthropic-version", "2023-06-01");
        try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        String resp = new String(Codec.readAll(in), "UTF-8");
        if (code >= 400) throw new IOException("HTTP " + code + ": " + resp);
        JSONArray ct = new JSONObject(resp).getJSONArray("content");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ct.length(); i++) if ("text".equals(ct.getJSONObject(i).optString("type"))) b.append(ct.getJSONObject(i).getString("text"));
        return b.toString();
    }

    public static final String SYSTEM = "Voce ajuda com a linguagem SPWN (Spu7Nix) e com edicao de niveis de Geometry Dash. "
            + "Nao invente sintaxe: se nao tiver certeza de algo do SPWN, diga. Responda em portugues, de forma curta.";
  }
