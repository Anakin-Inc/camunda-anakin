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

class BrowserTaskConnectorFunctionTest {

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
  void executeSubmitsAsyncAndReturnsResultOnCompletion() throws Exception {
    server.createContext("/ai/evaluate", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes());
      assertThat(body).contains("\"async\":true").contains("\"prompt\":\"find the cheapest TV\"");
      writeJson(exchange, 200, "{\"workflow_id\":\"wf-1\",\"status\":\"running\"}");
    });
    server.createContext("/ai/jobs/wf-1", exchange -> writeJson(exchange, 200,
        "{\"status\":\"completed\",\"result\":{\"price\":399}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "find the cheapest TV"))
            .build();

    BrowserTaskConnectorFunction function =
        new BrowserTaskConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("price")).isEqualTo(399);
  }

  @Test
  void executeReturnsWholeJobWhenNoResultField() throws Exception {
    server.createContext("/ai/evaluate", exchange ->
        writeJson(exchange, 200, "{\"workflow_id\":\"wf-2\",\"status\":\"running\"}"));
    server.createContext("/ai/jobs/wf-2", exchange -> writeJson(exchange, 200,
        "{\"status\":\"completed\",\"run_id\":\"r-1\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "click the button"))
            .build();

    BrowserTaskConnectorFunction function =
        new BrowserTaskConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("run_id")).isEqualTo("r-1");
  }

  @Test
  void executeThrowsAnakinJobFailedOnFailedStatus() throws Exception {
    server.createContext("/ai/evaluate", exchange ->
        writeJson(exchange, 200, "{\"workflow_id\":\"wf-3\",\"status\":\"running\"}"));
    server.createContext("/ai/jobs/wf-3", exchange -> writeJson(exchange, 200,
        "{\"status\":\"failed\",\"error\":\"could not find element\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "click the button"))
            .build();

    BrowserTaskConnectorFunction function =
        new BrowserTaskConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Browser task failed: could not find element")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_JOB_FAILED"));
  }

  @Test
  void executeThrowsAnakinJobTimedOutOnTimedOutStatus() throws Exception {
    server.createContext("/ai/evaluate", exchange ->
        writeJson(exchange, 200, "{\"workflow_id\":\"wf-4\",\"status\":\"running\"}"));
    server.createContext("/ai/jobs/wf-4", exchange -> writeJson(exchange, 200,
        "{\"status\":\"timed_out\",\"error\":\"exceeded max runtime\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "click the button"))
            .build();

    BrowserTaskConnectorFunction function =
        new BrowserTaskConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Browser task timed_out: exceeded max runtime")
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_JOB_TIMED_OUT"));
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
