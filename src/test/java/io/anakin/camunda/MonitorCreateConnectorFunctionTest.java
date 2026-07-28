package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorCreateConnectorFunctionTest {

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
  void executeOnlySendsFieldsTheCallerSet() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/monitors", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"id\":\"mon-1\",\"url\":\"https://example.com\"}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "url", "https://example.com", "intervalMinutes", 30))
            .build();

    MonitorCreateConnectorFunction function =
        new MonitorCreateConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    assertThat(capturedBody.toString())
        .contains("\"url\":\"https://example.com\"")
        .contains("\"intervalMinutes\":30")
        .doesNotContain("scope")
        .doesNotContain("watchMode")
        .doesNotContain("aiMode");

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("id")).isEqualTo("mon-1");
  }

  @Test
  void executeSendsOptionalFieldsWhenSet() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/monitors", exchange -> {
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"id\":\"mon-2\"}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of(
                "apiKey", "k",
                "url", "https://example.com",
                "intervalMinutes", 60,
                "scope", "site",
                "aiMode", true))
            .build();

    MonitorCreateConnectorFunction function =
        new MonitorCreateConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(capturedBody.toString())
        .contains("\"scope\":\"site\"")
        .contains("\"aiMode\":true");
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
