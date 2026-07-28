package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Connector-level test: exercises {@link UrlScraperConnectorFunction#execute} through the real
 * {@link OutboundConnectorContextBuilder} (secret substitution, Bean Validation via {@code
 * bindVariables}) against a local mock server standing in for {@code api.anakin.io} -- the same
 * dependency-injection pattern real Camunda connectors use for testability (e.g. {@code
 * SendGridFunction(SendGridClientSupplier)}, {@code TextractConnectorFunction(clientSupplier,
 * ...)} in camunda/connectors).
 */
class UrlScraperConnectorFunctionTest {

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
  void executeSubmitsPollsAndReturnsResultOnCompletion() throws Exception {
    AtomicInteger pollCount = new AtomicInteger(0);
    server.createContext("/url-scraper", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      assertThat(exchange.getRequestHeaders().getFirst("X-API-Key")).isEqualTo("real-secret-key");
      writeJson(exchange, 200, "{\"jobId\":\"job-42\",\"status\":\"pending\"}");
    });
    server.createContext("/url-scraper/job-42", exchange -> {
      int call = pollCount.incrementAndGet();
      String status = call < 2 ? "processing" : "completed";
      writeJson(exchange, 200, "{\"status\":\"" + status + "\",\"markdown\":\"# Hello\"}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of(
                "apiKey", "{{secrets.ANAKIN_API_KEY}}",
                "url", "https://example.com",
                "useBrowser", false,
                "generateJson", false,
                "forceFresh", false))
            .secret("ANAKIN_API_KEY", "real-secret-key")
            .build();

    UrlScraperConnectorFunction function =
        new UrlScraperConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    Object result = function.execute(context);

    assertThat(result).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
    assertThat(resultMap.get("markdown")).isEqualTo("# Hello");
    assertThat(pollCount.get()).isEqualTo(2);
  }

  @Test
  void executeThrowsConnectorExceptionWithAnakinJobFailedCodeOnFailedJob() throws Exception {
    server.createContext("/url-scraper", exchange -> writeJson(exchange, 200, "{\"jobId\":\"job-99\",\"status\":\"pending\"}"));
    server.createContext("/url-scraper/job-99", exchange -> writeJson(exchange, 200, "{\"status\":\"failed\",\"error\":\"could not render page\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com"))
            .build();

    UrlScraperConnectorFunction function =
        new UrlScraperConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("could not render page")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_JOB_FAILED"));
  }

  @Test
  void executeThrowsConnectorExceptionWithAnakinApiErrorCodeOnHttpError() throws Exception {
    server.createContext("/url-scraper", exchange -> writeJson(exchange, 401, "{\"error\":\"Invalid API key\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "bad-key", "url", "https://example.com"))
            .build();

    UrlScraperConnectorFunction function =
        new UrlScraperConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Invalid API key")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_API_ERROR"));
  }

  @Test
  void defaultFormatsIsMarkdownOnlyWhenOmitted() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/url-scraper", exchange -> {
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"jobId\":\"job-100\",\"status\":\"pending\"}");
    });
    server.createContext("/url-scraper/job-100", exchange -> writeJson(exchange, 200, "{\"status\":\"completed\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com"))
            .build();

    UrlScraperConnectorFunction function =
        new UrlScraperConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(capturedBody.toString()).contains("\"formats\":[\"markdown\"]");
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
