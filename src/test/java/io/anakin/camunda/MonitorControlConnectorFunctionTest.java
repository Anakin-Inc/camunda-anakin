package io.anakin.camunda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorControlConnectorFunctionTest {

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
  void executePausesMonitorViaPost() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    server.createContext("/monitors/mon-1/pause", exchange -> {
      method.set(exchange.getRequestMethod());
      writeJson(exchange, 200, "{\"id\":\"mon-1\",\"isActive\":false}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "monitorId", "mon-1", "action", "pause"))
            .build();

    MonitorControlConnectorFunction function =
        new MonitorControlConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(method.get()).isEqualTo("POST");
  }

  @Test
  void executeRunsRunNowActionAgainstRunPath() throws Exception {
    server.createContext("/monitors/mon-1/run", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "monitorId", "mon-1", "action", "run_now"))
            .build();

    MonitorControlConnectorFunction function =
        new MonitorControlConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("status")).isEqualTo("ok");
  }

  @Test
  void executeDeletesMonitorViaDelete() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    server.createContext("/monitors/mon-1", exchange -> {
      method.set(exchange.getRequestMethod());
      writeJson(exchange, 200, "{\"deleted\":true}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "monitorId", "mon-1", "action", "delete"))
            .build();

    MonitorControlConnectorFunction function =
        new MonitorControlConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);

    assertThat(method.get()).isEqualTo("DELETE");
  }

  @Test
  void executeThrowsConnectorExceptionOnUnknownAction() throws Exception {
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "monitorId", "mon-1", "action", "explode"))
            .build();

    MonitorControlConnectorFunction function =
        new MonitorControlConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("ANAKIN_INVALID_INPUT"));
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
