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

class AgenticSearchConnectorFunctionTest {

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
  void executeSubmitsPollsAndReturnsGeneratedJsonOnCompletion() throws Exception {
    server.createContext("/agentic-search", exchange -> writeJson(exchange, 200, "{\"jobId\":\"job-7\",\"status\":\"pending\"}"));
    server.createContext("/agentic-search/job-7", exchange -> writeJson(exchange, 200,
        "{\"status\":\"completed\",\"generatedJson\":{\"summary\":\"3 tools compared\"}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "compare project management tools"))
            .build();

    AgenticSearchConnectorFunction function =
        new AgenticSearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    Object result = function.execute(context);

    assertThat(result).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
  }

  @Test
  void useBrowserDefaultsToTrueWhenOmitted() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/agentic-search", exchange -> {
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"jobId\":\"job-8\",\"status\":\"pending\"}");
    });
    server.createContext("/agentic-search/job-8", exchange -> writeJson(exchange, 200, "{\"status\":\"completed\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "research topic"))
            .build();

    AgenticSearchConnectorFunction function =
        new AgenticSearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(capturedBody.toString()).contains("\"useBrowser\":true");
  }

  @Test
  void executeThrowsConnectorExceptionWithAnakinJobFailedCodeOnFailedJob() throws Exception {
    server.createContext("/agentic-search", exchange -> writeJson(exchange, 200, "{\"jobId\":\"job-9\",\"status\":\"pending\"}"));
    server.createContext("/agentic-search/job-9", exchange -> writeJson(exchange, 200, "{\"status\":\"failed\",\"error\":\"no results found\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "prompt", "research topic"))
            .build();

    AgenticSearchConnectorFunction function =
        new AgenticSearchConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("no results found")
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
