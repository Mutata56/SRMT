package mutata56.com.github.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;

/**
 * Простой клиент для публичного API LibreTranslate.
 */
public class LibreTranslateService {

    private static final String API_URL = "http://localhost:5000/translate";
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Переводит текст через LibreTranslate.
     * @param text    исходный текст
     * @param srcLang код исходного языка ("ru" или "en")
     * @param tgtLang код целевого языка ("ru" или "en")
     * @return переведённый текст
     */
    public String translate(String text, String srcLang, String tgtLang) {
        try {
            JSONObject body = new JSONObject()
                    .put("q", text)
                    .put("source", srcLang)
                    .put("target", tgtLang)
                    .put("format", "text");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(resp.body());
            return json.getString("translatedText");
        } catch (Exception e) {
            // при ошибке возвращаем исходный текст
            return text;
        }
    }
}
