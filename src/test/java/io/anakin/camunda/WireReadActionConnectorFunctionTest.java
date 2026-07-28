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

class WireReadActionConnectorFunctionTest {

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
  void executeReturnsInlineDataWithoutPollingForSynchronousAction() throws Exception {
    server.createContext("/wire/task", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      String body = new String(exchange.getRequestBody().readAllBytes());
      assertThat(body).contains("\"action_id\":\"walmart.get_product\"");
      writeJson(exchange, 200, "{\"status\":\"completed\",\"data\":{\"price\":19.99}}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "actionId", "walmart.get_product"))
            .build();

    WireReadActionConnectorFunction function =
        new WireReadActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
    assertThat(resultMap).containsKey("data");
  }

  @Test
  void executePollsWireJobsWhenJobIdReturned() throws Exception {
    server.createContext("/wire/task", exchange ->
        writeJson(exchange, 200, "{\"status\":\"processing\",\"job_id\":\"wj-1\"}"));
    server.createContext("/wire/jobs/wj-1", exchange ->
        writeJson(exchange, 200, "{\"status\":\"completed\",\"data\":{\"items\":[1,2,3]}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "actionId", "amazon.search_products"))
            .build();

    WireReadActionConnectorFunction function =
        new WireReadActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
  }

  @Test
  void executeThrowsAnakinJobFailedWithStructuredErrorMessage() throws Exception {
    server.createContext("/wire/task", exchange ->
        writeJson(exchange, 200, "{\"status\":\"processing\",\"job_id\":\"wj-2\"}"));
    server.createContext("/wire/jobs/wj-2", exchange -> writeJson(exchange, 200,
        "{\"status\":\"failed\",\"error\":{\"code\":\"AUTH_REQUIRED\",\"message\":\"sign in first\"}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "actionId", "linkedin.read_profile"))
            .build();

    WireReadActionConnectorFunction function =
        new WireReadActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("sign in first (AUTH_REQUIRED)")
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
