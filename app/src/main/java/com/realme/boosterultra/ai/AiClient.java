package com.realme.boosterultra.ai;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Cérebro do Jarvis: tenta IA 1 (Gemini), IA 2 (Groq), IA 3 (OpenRouter) e IA local, em ordem. */
public final class AiClient {
    private AiClient() {}

    /** {nome, chave prefs, base, modelo padrão} */
    public static final String[][] SLOTS = {
            {"Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"},
            {"Groq", "groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
            {"OpenRouter", "openrouter", "https://openrouter.ai/api/v1", "openrouter/auto"}};

    public static final String SERVER_URL = "https://proud-grass-4ac7.narutouzomaki538.workers.dev";
    public static final String LOCAL_BASE = "http://127.0.0.1:11434/v1";
    public static final String LOCAL_MODEL = "llama3.2:3b";
    public static final String DEFAULT_PERSONA = "Você é o JARVIS, assistente pessoal do usuário: educado, elegante, "
            + "levemente irônico e muito eficiente. Chame o usuário de \"senhor\". Dê respostas curtas e rápidas, "
            + "sem enrolação, a não ser que ele peça detalhes.";

    public static final class Reply {
        public String text, from;
    }

    public static boolean isLocal(String base) {
        String b = base.toLowerCase(java.util.Locale.ROOT);
        return b.startsWith("http://127.0.0.1") || b.startsWith("http://localhost");
    }

    /** Copia a chave da versão anterior (um provedor só) para o novo esquema de 3 IAs. */
    public static void migrate(Context c) {
        SharedPreferences p = ProfileManager.prefs(c);
        if (p.getBoolean("ai_migrated", false)) return;
        SharedPreferences.Editor e = p.edit().putBoolean("ai_migrated", true);
        String oldKey = p.getString("ai_key", "");
        String oldBase = p.getString("ai_base", "");
        if (!oldKey.isEmpty()) {
            for (String[] s : SLOTS) if (s[2].equals(oldBase)) e.putString("key_" + s[1], oldKey);
        }
        if (isLocal(oldBase)) e.putBoolean("local_on", true).putString("local_base", oldBase);
        if ("Coruja".equals(p.getString("ai_name", "Coruja"))) e.putString("ai_name", "Jarvis");
        e.apply();
    }

    public static boolean hasBrain(Context c) {
        SharedPreferences p = ProfileManager.prefs(c);
        if (p.getBoolean("local_on", false) || p.getBoolean("srv_on", false)) return true;
        for (String[] s : SLOTS) if (!p.getString("key_" + s[1], "").trim().isEmpty()) return true;
        return false;
    }

    public static String systemPrompt(Context c) {
        SharedPreferences p = ProfileManager.prefs(c);
        String name = p.getString("ai_name", "Jarvis");
        String persona = p.getString("ai_persona", DEFAULT_PERSONA);
        if (persona.trim().isEmpty()) persona = DEFAULT_PERSONA;
        return "Seu nome é " + name + ". " + persona + "\n\n"
                + "Você é o assistente do app Realme Booster Ultra, no celular " + Build.MANUFACTURER + " " + Build.MODEL
                + " (Android " + Build.VERSION.RELEASE + ") do usuário. Responda em português do Brasil. "
                + "Suas respostas podem ser lidas em voz alta: evite listas longas e emojis.\n"
                + "Você controla o celular. Quando o usuário pedir, escreva no FINAL da resposta uma linha "
                + "exatamente assim: [ACAO:nome] (pode usar mais de uma). Ações:\n"
                + "lanterna_on, lanterna_off, perfil_jogo, perfil_economia, perfil_normal, liberar_ram, "
                + "super_turbo, brilho:0-100, volume:0-100, abrir:NOME DO APP, jogar:NOME DO JOGO, "
                + "wifi, bluetooth, bateria.\nSó use ações quando o usuário pedir.";
    }

    public static Reply chat(Context c, JSONArray history) throws Exception {
        SharedPreferences p = ProfileManager.prefs(c);
        List<String[]> order = new ArrayList<>(); // {nome, base, modelo, chave}
        if (p.getBoolean("srv_on", false)) {
            order.add(new String[]{"Servidor Jarvis", "SERVER", "", ""});
        }
        if (p.getBoolean("local_on", false)) {
            order.add(new String[]{"IA local", p.getString("local_base", LOCAL_BASE),
                    p.getString("local_model", LOCAL_MODEL), ""});
        }
        for (String[] s : SLOTS) {
            String key = p.getString("key_" + s[1], "").trim();
            if (!key.isEmpty()) order.add(new String[]{s[0], s[2], p.getString("model_" + s[1], s[3]), key});
        }
        if (order.isEmpty()) throw new Exception("Senhor, ainda não tenho cérebro. Coloque pelo menos uma chave na aba JARVIS.");

        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt(c)));
        int start = Math.max(0, history.length() - 20);
        for (int i = start; i < history.length(); i++) msgs.put(history.getJSONObject(i));

        StringBuilder errors = new StringBuilder();
        for (String[] o : order) {
            try {
                Reply r = new Reply();
                r.text = "SERVER".equals(o[1]) ? callServer(p, msgs) : call(o[1], o[2], o[3], msgs);
                r.from = o[0];
                return r;
            } catch (Exception e) {
                errors.append(o[0]).append(": ").append(e.getMessage()).append("\n");
            }
        }
        throw new Exception("Todas as IAs falharam:\n" + errors.toString().trim());
    }

    /** Servidor Cloudflare do Jarvis: envia nos formatos mais comuns e entende várias respostas. */
    private static String callServer(SharedPreferences p, JSONArray msgs) throws Exception {
        String url = p.getString("srv_url", SERVER_URL).trim().replaceAll("/+$", "");
        String path = p.getString("srv_path", "/chat").trim();
        if (!path.isEmpty() && !path.startsWith("/")) path = "/" + path;
        String senha = p.getString("srv_senha", "");
        String last = "";
        JSONArray hist = new JSONArray();
        String system = "";
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.getJSONObject(i);
            if ("system".equals(m.optString("role"))) { system = m.optString("content"); continue; }
            hist.put(m);
            if ("user".equals(m.optString("role"))) last = m.optString("content");
        }
        JSONObject body = new JSONObject()
                .put("senha", senha).put("password", senha)
                .put("mensagem", last).put("message", last).put("pergunta", last).put("texto", last)
                .put("historico", hist).put("messages", msgs).put("sistema", system)
                .put("origem", "realmeboosterultra");
        HttpURLConnection con = (HttpURLConnection) new URL(url + path).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        if (!senha.isEmpty()) {
            con.setRequestProperty("X-Senha", senha);
            con.setRequestProperty("Authorization", "Bearer " + senha);
        }
        int code;
        try {
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            code = con.getResponseCode();
        } catch (java.net.UnknownHostException uh) {
            throw new Exception("sem internet");
        }
        InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
        String text = read(is).trim();
        con.disconnect();
        if (code == 401 || code == 403) throw new Exception("senha errada");
        if (code == 404 || code == 405) throw new Exception("caminho " + path + " não existe no servidor");
        if (code >= 400) throw new Exception("erro " + code + " " + (text.length() > 120 ? text.substring(0, 120) : text));
        String out = extract(text);
        if (out == null || out.trim().isEmpty()) throw new Exception("resposta vazia do servidor");
        return out;
    }

    private static String extract(String text) {
        if (!text.startsWith("{")) return text;
        try {
            JSONObject o = new JSONObject(text);
            if (o.has("erro") || o.has("error")) {
                Object e = o.has("erro") ? o.get("erro") : o.get("error");
                if (!(e instanceof Boolean) || (Boolean) e) throw new RuntimeException(String.valueOf(e));
            }
            String[] keys = {"resposta", "reply", "response", "texto", "text", "content", "answer", "output", "message", "mensagem"};
            for (String k : keys) {
                Object v = o.opt(k);
                if (v instanceof String && !((String) v).isEmpty()) return (String) v;
                if (v instanceof JSONObject && ((JSONObject) v).has("content")) return ((JSONObject) v).optString("content");
            }
            if (o.has("choices")) return o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content");
            if (o.has("candidates")) return o.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text");
            if (o.has("data")) return extract(o.get("data").toString());
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ignored) {}
        return null;
    }

    private static String call(String base, String model, String key, JSONArray msgs) throws Exception {
        base = base.replaceAll("/+$", "");
        boolean local = isLocal(base);
        JSONObject body = new JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.7);
        HttpURLConnection con = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(local ? 5000 : 15000);
        con.setReadTimeout(local ? 300000 : 60000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        if (!key.isEmpty()) con.setRequestProperty("Authorization", "Bearer " + key);
        con.setRequestProperty("X-Title", "Realme Booster Ultra Jarvis");
        int code;
        try {
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            code = con.getResponseCode();
        } catch (java.net.ConnectException ce) {
            throw new Exception(local ? "servidor local desligado" : "sem conexão");
        } catch (java.net.UnknownHostException uh) {
            throw new Exception("sem internet");
        }
        InputStream is = code < 400 ? con.getInputStream() : con.getErrorStream();
        String text = read(is);
        con.disconnect();
        if (code >= 400) {
            switch (code) {
                case 400: throw new Exception("pedido recusado (confira o modelo)");
                case 401: case 403: throw new Exception("chave inválida");
                case 404: throw new Exception("modelo não encontrado");
                case 429: throw new Exception("limite de uso atingido");
                default: throw new Exception("erro " + code);
            }
        }
        JSONObject r = new JSONObject(text);
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }
}
