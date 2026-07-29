package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiVisibilitySearchConnectorFunctionTest {

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
  void executeStripsFullContentByDefault() throws Exception {
    server.createContext("/ai-visibility/search", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-1\",\"status\":\"running\"}"));
    server.createContext("/ai-visibility/search/sv-1", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-1\",\"status\":\"completed\",\"results\":"
            + "[{\"source\":\"chatgpt\",\"summary\":\"...\",\"full_content\":\"very long text\"}]}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "query", "who makes the best CRM"))
            .build();

    AiVisibilitySearchConnectorFunction function =
        new AiVisibilitySearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("results");
    assertThat(results).hasSize(1);
    assertThat(results.get(0)).doesNotContainKey("full_content").containsEntry("source", "chatgpt");
  }

  @Test
  void executeKeepsFullContentWhenRequested() throws Exception {
    server.createContext("/ai-visibility/search", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-2\",\"status\":\"running\"}"));
    server.createContext("/ai-visibility/search/sv-2", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-2\",\"status\":\"completed\",\"results\":"
            + "[{\"source\":\"gemini\",\"full_content\":\"very long text\"}]}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of(
                "apiKey", "k",
                "query", "who makes the best CRM",
                "includeFullContent", true))
            .build();

    AiVisibilitySearchConnectorFunction function =
        new AiVisibilitySearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("results");
    assertThat(results.get(0)).containsEntry("full_content", "very long text");
  }

  @Test
  void executeReturnsFailedStatusRatherThanThrowing() throws Exception {
    server.createContext("/ai-visibility/search", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-3\",\"status\":\"running\"}"));
    server.createContext("/ai-visibility/search/sv-3", exchange -> writeJson(exchange, 200,
        "{\"search_id\":\"sv-3\",\"status\":\"failed\",\"results\":[]}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "query", "who makes the best CRM"))
            .build();

    AiVisibilitySearchConnectorFunction function =
        new AiVisibilitySearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("failed");
  }

  @Test
  void executeThrowsConnectorExceptionOnHttpError() throws Exception {
    server.createContext("/ai-visibility/search", exchange -> writeJson(exchange, 429, "{\"error\":\"Rate limit exceeded\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "query", "who makes the best CRM"))
            .build();

    AiVisibilitySearchConnectorFunction function =
        new AiVisibilitySearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Rate limit exceeded")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_API_ERROR"));
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
