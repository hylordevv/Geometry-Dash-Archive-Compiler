package com.hylor.gdac;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cliente de IA generico: Anthropic nativo + qualquer API compativel com OpenAI. */
public class Ai {

    static class Provider {
        final String name;
        final String url;
        final String model;
        final boolean anthropic;
        final boolean needsKey;

        Provider(String name, String url, String model, boolean anthropic, boolean needsKey) {
            this.name = name;
            this.url = url;
            this.model = model;
            this.anthropic = anthropic;
            this.needsKey = needsKey;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final Provider[] LIST = new Provider[] {
        new Provider("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", false, true),
        new Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", false, true),
        new Provider("Anthropic (Claude)", "https://api.anthropic.com/v1", "claude-sonnet-5-5", true, true),
        new Provider("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", false, true),
        new Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", false, true),
        new Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", false, true),
        new Provider("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", false, true),
        new Provider("xAI (Grok)", "https://api.x.ai/v1", "grok-3", false, true),
        new Provider("Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", false, true),
        new Provider("Ollama (local)", "http://127.0.0.1:11434/v1", "llama3.2", false, false),
        new Provider("Personalizado (compativel com OpenAI)", "", "", false, true)
    };

    static final String SYSTEM =
            "Voce e o assistente do app GD Archive Compiler, um editor de niveis do Geometry Dash. "
            + "Responda em portugues, curto e pratico. "
            + "Quando pedir objetos/niveis, responda com codigo num bloco ``` neste formato (uma linha por objeto): "
            + "Nome(opcao) pos(x,y) modificadores. Exemplos: "
            + "Bloco pos(15,15) | Espinho pos(45,15) rot(90) | Trigger(Cor) pos(300,15) rgb(255,0,0) tempo(0.5) alvocor(1) zlayer(3) zorder(2). "
            + "Nomes: Bloco, Laje, Espinho, EspinhoPequeno, PadAmarelo, PadAzul, PadRosa, OrbAmarelo, OrbAzul, OrbRosa, "
            + "GravidadeNormal, GravidadeInvertida, PortalCubo, PortalNave, PortalBola, PortalUFO, PortalWave, PortalRobo, PortalAranha, "
            + "Velocidade05, Velocidade1, Velocidade2, Velocidade3, Velocidade4, "
            + "Trigger(Mover), Trigger(Cor), Trigger(Alpha), Trigger(Toggle), Trigger(Spawn), Trigger(Girar), Trigger(Seguir), Trigger(Pulso), Trigger(Parar). "
            + "Qualquer outro ID: Objeto(123). "
            + "Modificadores: pos(x,y), rot(graus), flipx, flipy, escala(n), cor(n), cor2(n), alvocor(n), alvo(grupo), grupos(1,2,3), "
            + "tempo(s), rgb(r,g,b), mover(x,y), easing(n), opacidade(n), zlayer(n), zorder(n), camada1(n), camada2(n), "
            + "e P<numero>(valor) para propriedades sem nome. "
            + "A grade tem 30 unidades por bloco; o centro do primeiro bloco no chao e pos(15,15). "
            + "Nao use outro formato.";

    private static JSONObject msg(String role, String content) throws Exception {
        JSONObject m = new JSONObject();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    static String chat(String baseUrl, String key, String model, boolean anthropic,
                       List<String[]> msgs, String system) throws Exception {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.length() == 0) {
            throw new Exception("Informe a URL base do provedor.");
        }
        if (model.trim().length() == 0) {
            throw new Exception("Informe o modelo.");
        }
        String endpoint;
        if (anthropic) {
            endpoint = base + "/messages";
        } else if (base.endsWith("/chat/completions")) {
            endpoint = base;
        } else {
            endpoint = base + "/chat/completions";
        }

        JSONObject body = new JSONObject();
        body.put("model", model.trim());
        JSONArray arr = new JSONArray();
        if (anthropic) {
            body.put("max_tokens", 2048);
            body.put("system", system);
        } else {
            arr.put(msg("system", system));
        }
        for (int i = 0; i < msgs.size(); i++) {
            arr.put(msg(msgs.get(i)[0], msgs.get(i)[1]));
        }
        body.put("messages", arr);

        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("content-type", "application/json");
        if (anthropic) {
            c.setRequestProperty("x-api-key", key.trim());
            c.setRequestProperty("anthropic-version", "2023-06-01");
        } else if (key.trim().length() > 0) {
            c.setRequestProperty("Authorization", "Bearer " + key.trim());
        }
        if (endpoint.contains("openrouter")) {
            c.setRequestProperty("X-Title", "GD Archive Compiler");
        }
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(180000);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        String resp = is == null ? "" : new String(Codec.readAll(is), StandardCharsets.UTF_8);
        if (code >= 400) {
            if (resp.length() > 600) {
                resp = resp.substring(0, 600);
            }
            throw new Exception("Erro HTTP " + code + ": " + resp);
        }

        JSONObject j = new JSONObject(resp);
        StringBuilder out = new StringBuilder();
        if (anthropic) {
            JSONArray ct = j.getJSONArray("content");
            for (int i = 0; i < ct.length(); i++) {
                JSONObject part = ct.getJSONObject(i);
                if ("text".equals(part.optString("type"))) {
                    out.append(part.optString("text"));
                }
            }
        } else {
            JSONArray ch = j.getJSONArray("choices");
            Object content = ch.getJSONObject(0).getJSONObject("message").opt("content");
            if (content instanceof JSONArray) {
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    out.append(parts.getJSONObject(i).optString("text"));
                }
            } else if (content != null) {
                out.append(String.valueOf(content));
            }
        }
        return out.toString();
    }
}
