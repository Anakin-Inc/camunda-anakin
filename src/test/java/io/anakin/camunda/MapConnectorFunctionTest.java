package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapConnectorFunctionTest {

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
    server.createContext("/map", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      writeJson(exchange, 200, "{\"jobId\":\"job-1\",\"status\":\"pending\"}");
    });
    server.createContext("/map/job-1", exchange -> writeJson(exchange, 200,
        "{\"status\":\"completed\",\"links\":[\"https://example.com/a\"],\"totalLinks\":1}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com"))
            .build();

    MapConnectorFunction function = new MapConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
    assertThat(resultMap.get("totalLinks")).isEqualTo(1);
  }

  @Test
  void defaultsAreAppliedWhenOmitted() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/map", exchange -> {
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"jobId\":\"job-2\",\"status\":\"pending\"}");
    });
    server.createContext("/map/job-2", exchange -> writeJson(exchange, 200, "{\"status\":\"completed\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com"))
            .build();

    MapConnectorFunction function = new MapConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(capturedBody.toString())
        .contains("\"limit\":100")
        .contains("\"depth\":2")
        .contains("\"limitPerLevel\":100")
        .contains("\"includeSubdomains\":false")
        .contains("\"includeExternalLinks\":false")
        .contains("\"useBrowser\":false");
  }

  @Test
  void executeThrowsConnectorExceptionWithAnakinJobFailedCodeOnFailedJob() throws Exception {
    server.createContext("/map", exchange -> writeJson(exchange, 200, "{\"jobId\":\"job-3\",\"status\":\"pending\"}"));
    server.createContext("/map/job-3", exchange -> writeJson(exchange, 200, "{\"status\":\"failed\",\"error\":\"could not reach host\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com"))
            .build();

    MapConnectorFunction function = new MapConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("could not reach host")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_JOB_FAILED"));
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
