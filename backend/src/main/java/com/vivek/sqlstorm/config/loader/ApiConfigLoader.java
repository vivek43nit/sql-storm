package com.vivek.sqlstorm.config.loader;

import com.vivek.utils.parser.ConfigParserInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Loads config by fetching content from an HTTP API at startup.
 * The response body (XML or JSON in the same format as the file sources)
 * is written to a temp file and then parsed by the supplied parser.
 *
 * A restart is required to pick up changes from the API.
 */
public class ApiConfigLoader<T> implements ConfigLoaderStrategy<T> {
    private static final Logger log = LoggerFactory.getLogger(ApiConfigLoader.class);

    private final String url;
    private final String token;          // optional Bearer token
    private final int timeoutSeconds;
    private final String fileExtension;  // "xml" or "json" — for temp file naming
    private final ConfigParserInterface<T> parser;

    public ApiConfigLoader(String url,
                           String token,
                           int timeoutSeconds,
                           String format,
                           ConfigParserInterface<T> parser) {
        this.url = url;
        this.token = token;
        this.timeoutSeconds = timeoutSeconds;
        this.fileExtension = format;
        this.parser = parser;
    }

    @Override
    public T load() throws ConfigLoadException {
        log.info("Fetching config from API: {}", url);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET();

            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<byte[]> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ConfigLoadException(
                        "API returned HTTP " + response.statusCode() + " for config URL: " + url);
            }

            File tmp = writeTempFile(response.body());
            T result = parser.parse(tmp);
            if (result == null) {
                throw new ConfigLoadException("Parser returned null for content fetched from: " + url);
            }
            log.info("Loaded config from API: {}", url);
            return result;
        } catch (ConfigLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to load config from API: " + url, e);
        }
    }

    private File writeTempFile(byte[] content) throws IOException {
        File tmp = File.createTempFile("fkblitz-cfg-api-", "." + fileExtension);
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content);
        }
        return tmp;
    }
}
