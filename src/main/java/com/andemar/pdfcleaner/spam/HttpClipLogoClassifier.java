package com.andemar.pdfcleaner.spam;

import com.andemar.pdfcleaner.model.ImageBlock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Pluggable HTTP client communicating with an external ML/CLIP vision microservice
 * to classify candidate logos.
 */
public class HttpClipLogoClassifier implements MlLogoClassifier {

    private final String endpointUrl;
    private final HttpClient httpClient;
    private final Duration timeout;

    public HttpClipLogoClassifier(String endpointUrl) {
        this(endpointUrl, Duration.ofSeconds(3));
    }

    public HttpClipLogoClassifier(String endpointUrl, Duration timeout) {
        this.endpointUrl = endpointUrl;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public boolean isSpamLogo(ImageBlock image) {
        if (endpointUrl == null || endpointUrl.isBlank() || image.getRawBytes() == null) {
            return false;
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(image.getRawBytes());
            String jsonPayload = String.format("{\"id\":\"%s\",\"image_base64\":\"%s\"}", 
                    escapeJson(image.getId()), base64Image);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().toLowerCase();
                // Simple resilient JSON parsing for {"is_logo": true} or {"spam": true}
                return body.contains("\"is_logo\": true") || 
                       body.contains("\"is_logo\":true") ||
                       body.contains("\"spam\": true") ||
                       body.contains("\"spam\":true");
            }
        } catch (Exception e) {
            // Silently fall back to heuristic results if ML endpoint is unreachable
        }
        return false;
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String getName() {
        return "HttpClipLogoClassifier (" + endpointUrl + ")";
    }
}
