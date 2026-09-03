package de.janm.stundennachweis;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class OpenAiClient {
    static final String MODEL = "gpt-5.6-luna";
    private final SecretStore secrets;

    OpenAiClient(SecretStore secrets) { this.secrets = secrets; }

    String professionalise(String spoken) throws Exception {
        String key = secrets.apiKey();
        if (key.isBlank()) throw new IllegalStateException("Kein API-Schlüssel gespeichert");
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("instructions", "Du korrigierst deutsche Stundennachweise einer Förderschule. Formuliere die freie Sprache fachlich, knapp und fehlerfrei. Nomen korrekt großschreiben. Freiarbeit ist ein Wort. ALDI immer groß. Fach voranstellen, z. B. 'Deutsch: ...'. Keine Erklärungen, keine Anführungszeichen, genau ein fertiger Eintrag mit abschließendem Punkt.");
        body.put("input", spoken);
        body.put("max_output_tokens", 180);
        body.put("reasoning", new JSONObject().put("effort", "none"));
        JSONObject response = post(body, key);
        String result = outputText(response);
        if (result.isBlank()) throw new IllegalStateException("Leere KI-Antwort");
        return GermanEntryFormatter.finaliseAi(result);
    }

    boolean test() {
        try { return !professionalise("deutsch frei arbeit").isBlank(); }
        catch (Exception e) { return false; }
    }

    private JSONObject post(JSONObject body, String key) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) json.append(line);
        } finally { connection.disconnect(); }
        if (status < 200 || status >= 300) throw new IllegalStateException("OpenAI-Fehler " + status);
        return new JSONObject(json.toString());
    }

    private String outputText(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONArray content = output.optJSONObject(i) == null ? null : output.optJSONObject(i).optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject item = content.optJSONObject(j);
                if (item != null && "output_text".equals(item.optString("type"))) return item.optString("text", "");
            }
        }
        return "";
    }
}
