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

class WireWriteActionConnectorFunctionTest {

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
  void executeSendsCredentialIdWhenSupplied() throws Exception {
    StringBuilder capturedBody = new StringBuilder();
    server.createContext("/wire/task", exchange -> {
      capturedBody.append(new String(exchange.getRequestBody().readAllBytes()));
      writeJson(exchange, 200, "{\"status\":\"completed\",\"data\":{\"added\":true}}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of(
                "apiKey", "k",
                "actionId", "amazon.add_to_cart",
                "credentialId", "cred-1"))
            .build();

    WireWriteActionConnectorFunction function =
        new WireWriteActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    assertThat(capturedBody.toString()).contains("\"credential_id\":\"cred-1\"");
    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
  }

  @Test
  void executePollsWireJobsWhenJobIdReturned() throws Exception {
    server.createContext("/wire/task", exchange ->
        writeJson(exchange, 200, "{\"status\":\"processing\",\"job_id\":\"wj-9\"}"));
    server.createContext("/wire/jobs/wj-9", exchange ->
        writeJson(exchange, 200, "{\"status\":\"completed\",\"data\":{\"submitted\":true}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "actionId", "site.submit_form"))
            .build();

    WireWriteActionConnectorFunction function =
        new WireWriteActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("completed");
  }

  @Test
  void executeThrowsAnakinJobFailedOnFailedJobWithoutStructuredMessage() throws Exception {
    server.createContext("/wire/task", exchange ->
        writeJson(exchange, 200, "{\"status\":\"processing\",\"job_id\":\"wj-10\"}"));
    server.createContext("/wire/jobs/wj-10", exchange ->
        writeJson(exchange, 200, "{\"status\":\"failed\",\"error\":{\"code\":\"FORBIDDEN\"}}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "actionId", "site.submit_form"))
            .build();

    WireWriteActionConnectorFunction function =
        new WireWriteActionConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Wire job failed: FORBIDDEN")
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
