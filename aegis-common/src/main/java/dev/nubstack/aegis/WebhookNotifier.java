package dev.nubstack.aegis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional Discord webhook sender. Failures are logged and never thrown to callers.
 */
public final class WebhookNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookNotifier.class);

    private final String webhookUrl;
    private final HttpClient client;

    public WebhookNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean enabled() {
        return webhookUrl != null;
    }

    public void notifyPunishment(Punishment punishment) {
        if (!enabled()) {
            return;
        }
        String content = escape(punishment.type().name())
                + " "
                + escape(punishment.targetName())
                + " by "
                + escape(punishment.staffName())
                + ": "
                + escape(punishment.reason());
        String body = "{\"content\":\"" + content + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(Objects.requireNonNull(webhookUrl)))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOGGER.warn("Discord webhook failed", error);
                    }
                });
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
