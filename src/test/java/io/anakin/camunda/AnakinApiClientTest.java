package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link AnakinApiClient} against a real local HTTP server ({@code
 * com.sun.net.httpserver.HttpServer}, built into the JDK -- no extra test dependency needed)
 * instead of the real api.anakin.io, since this sandbox has no network access to it and no live
 * API key. Does NOT touch the Connector SDK at all: {@code AnakinApiClient} is deliberately plain
 * Java precisely so this class can be tested in isolation, mirroring the same pattern used for
 * {@code AnakinApiClient} in this session's Appian plug-in build.
 */
class AnakinApiClientTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postReturnsParsedJsonOnSuccess() throws Exception {
    server.createContext("/search", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-key");
      writeJson(exchange, 200, "{\"id\":\"abc123\",\"results\":[{\"url\":\"https://example.com\"}]}");
    });
    server.start();

    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl);
    Map<String, Object> response = client.post("/search", Map.of("prompt", "test", "limit", 5));

    assertThat(response.get("id")).isEqualTo("abc123");
    assertThat(response.get("results")).isInstanceOf(java.util.List.class);
  }

  @Test
  void getSendsApiKeyHeader() throws Exception {
    server.createContext("/sessions", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("GET");
      assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("test-key");
      writeJson(exchange, 200, "{\"sessions\":[]}");
    });
    server.start();

    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl);
    Map<String, Object> response = client.get("/sessions");

    assertThat(response).containsKey("sessions");
  }

  @Test
  void errorResponseThrowsWithApiMessage() throws Exception {
    server.createContext("/url-scraper", exchange -> writeJson(exchange, 401, "{\"error\":\"Invalid API key\"}"));
    server.start();

    AnakinApiClient client = new AnakinApiClient("bad-key", baseUrl);

    assertThatThrownBy(() -> client.post("/url-scraper", Map.of("url", "https://example.com")))
        .isInstanceOf(AnakinApiException.class)
        .hasMessage("Invalid API key")
        .satisfies(e -> assertThat(((AnakinApiException) e).getStatusCode()).isEqualTo(401));
  }

  @Test
  void malformedJsonThrowsAnakinApiException() throws Exception {
    server.createContext("/search", exchange -> {
      String body = "{not valid json";
      exchange.sendResponseHeaders(200, body.length());
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body.getBytes());
      }
    });
    server.start();

    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl);

    assertThatThrownBy(() -> client.get("/search"))
        .isInstanceOf(AnakinApiException.class)
        .hasMessageContaining("malformed JSON");
  }

  @Test
  void pollUntilTerminalPollsUntilCompleted() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    server.createContext("/url-scraper/job-1", exchange -> {
      int call = callCount.incrementAndGet();
      String status = call < 3 ? "processing" : "completed";
      writeJson(exchange, 200, "{\"status\":\"" + status + "\",\"markdown\":\"hello\"}");
    });
    server.start();

    // Exercises the real pollUntilTerminal loop (2s fixed poll interval, so this test takes ~4s
    // to reach the 3rd, terminal, response).
    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl);
    Map<String, Object> result = client.pollUntilTerminal("/url-scraper/job-1");

    assertThat(result.get("status")).isEqualTo("completed");
    assertThat(result.get("markdown")).isEqualTo("hello");
    assertThat(callCount.get()).isEqualTo(3);
  }

  @Test
  void pollUntilTerminalReturnsImmediatelyOnFailed() throws Exception {
    server.createContext("/agentic-search/job-2", exchange -> writeJson(exchange, 200, "{\"status\":\"failed\",\"error\":\"job blew up\"}"));
    server.start();

    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl);
    Map<String, Object> result = client.pollUntilTerminal("/agentic-search/job-2");

    assertThat(result.get("status")).isEqualTo("failed");
    assertThat(result.get("error")).isEqualTo("job blew up");
  }

  @Test
  void requireJobIdAcceptsJobIdJobUnderscoreIdOrId() throws Exception {
    assertThat(AnakinApiClient.requireJobId(Map.of("jobId", "j1"))).isEqualTo("j1");
    assertThat(AnakinApiClient.requireJobId(Map.of("job_id", "j2"))).isEqualTo("j2");
    assertThat(AnakinApiClient.requireJobId(Map.of("id", "j3"))).isEqualTo("j3");
  }

  @Test
  void requireJobIdThrowsWhenMissing() {
    assertThatThrownBy(() -> AnakinApiClient.requireJobId(Map.of("status", "pending")))
        .isInstanceOf(AnakinApiException.class)
        .hasMessageContaining("did not include a job id");
  }

  @Test
  void customHttpClientConstructorIsUsable() throws Exception {
    server.createContext("/search", exchange -> writeJson(exchange, 200, "{\"id\":\"x\"}"));
    server.start();

    AnakinApiClient client = new AnakinApiClient("test-key", baseUrl, HttpClient.newHttpClient());
    Map<String, Object> response = client.get("/search");
    assertThat(response.get("id")).isEqualTo("x");
  }

  private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes();
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }
}
