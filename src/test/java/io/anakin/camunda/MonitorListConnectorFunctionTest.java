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

class MonitorListConnectorFunctionTest {

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
  void executeListsAllMonitorsWhenIdOmitted() throws Exception {
    server.createContext("/monitors", exchange -> {
      assertThat(exchange.getRequestURI().getPath()).isEqualTo("/monitors");
      writeJson(exchange, 200, "{\"monitors\":[]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(Map.of("apiKey", "k")).build();

    MonitorListConnectorFunction function =
        new MonitorListConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsKey("monitors");
  }

  @Test
  void executeFetchesOneMonitorWhenMonitorIdSupplied() throws Exception {
    server.createContext("/monitors/mon-1", exchange ->
        writeJson(exchange, 200, "{\"id\":\"mon-1\",\"isActive\":true}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "monitorId", "mon-1"))
            .build();

    MonitorListConnectorFunction function =
        new MonitorListConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("id")).isEqualTo("mon-1");
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
