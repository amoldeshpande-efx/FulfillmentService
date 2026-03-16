package com.equifax.ews.vs.ici;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import org.springframework.boot.SpringApplication;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Cloud Functions Gen 1 adapter for the Spring Boot FulfillmentService.
 *
 * Starts the Spring Boot embedded server on first invocation and proxies
 * all HTTP requests to it. This allows the full Spring MVC application
 * (REST endpoints, MCP server, Swagger UI) to run inside a Cloud Function.
 */
public class FulfillmentFunction implements HttpFunction {

    private static final Logger logger = Logger.getLogger(FulfillmentFunction.class.getName());
    private static final int INTERNAL_PORT = 8081;
    private static final Object LOCK = new Object();
    private static volatile boolean started = false;
    private static HttpClient client;

    private void ensureStarted() {
        if (!started) {
            synchronized (LOCK) {
                if (!started) {
                    logger.info("Cold start: bootstrapping Spring Boot on port " + INTERNAL_PORT);
                    System.setProperty("server.port", String.valueOf(INTERNAL_PORT));
                    SpringApplication.run(Application.class);
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(30))
                            .build();
                    started = true;
                    logger.info("Spring Boot application ready");
                }
            }
        }
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        ensureStarted();

        String path = request.getPath();
        String query = request.getQuery().orElse("");
        String targetUrl = "http://localhost:" + INTERNAL_PORT + path
                + (query.isEmpty() ? "" : "?" + query);

        // Build proxied request
        java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(300));

        // Copy headers (skip hop-by-hop headers)
        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            String key = entry.getKey().toLowerCase();
            if ("host".equals(key) || "content-length".equals(key)
                    || "transfer-encoding".equals(key) || "connection".equals(key)) {
                continue;
            }
            for (String value : entry.getValue()) {
                reqBuilder.header(entry.getKey(), value);
            }
        }

        // Set method and body
        String method = request.getMethod().toUpperCase();
        switch (method) {
            case "POST", "PUT", "PATCH" -> {
                byte[] body = request.getInputStream().readAllBytes();
                reqBuilder.method(method, BodyPublishers.ofByteArray(body));
            }
            case "DELETE" -> reqBuilder.DELETE();
            default -> reqBuilder.GET();
        }

        try {
            java.net.http.HttpResponse<byte[]> proxyResp = client.send(
                    reqBuilder.build(), BodyHandlers.ofByteArray());

            response.setStatusCode(proxyResp.statusCode());

            proxyResp.headers().map().forEach((key, values) -> {
                if (!"transfer-encoding".equalsIgnoreCase(key)) {
                    for (String value : values) {
                        response.appendHeader(key, value);
                    }
                }
            });

            byte[] responseBody = proxyResp.body();
            if (responseBody != null && responseBody.length > 0) {
                response.getOutputStream().write(responseBody);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error proxying request to Spring Boot", e);
            response.setStatusCode(502);
            response.getWriter().write("{\"error\":\"Internal proxy error\"}");
        }
    }
}