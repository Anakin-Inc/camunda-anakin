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

class SessionListConnectorFunctionTest {

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
  void executeFiltersByDomainWhenSupplied() throws Exception {
    server.createContext("/sessions", exchange -> {
      assertThat(exchange.getRequestURI().getQuery()).isEqualTo("domain=amazon.com");
      writeJson(exchange, 200, "{\"sessions\":[{\"id\":\"sess-1\",\"domain\":\"amazon.com\"}]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "domain", "amazon.com"))
            .build();

    SessionListConnectorFunction function =
        new SessionListConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsKey("sessions");
  }

  @Test
  void executeOmitsQueryWhenDomainNotSupplied() throws Exception {
    server.createContext("/sessions", exchange -> {
      assertThat(exchange.getRequestURI().getQuery()).isNull();
      writeJson(exchange, 200, "{\"sessions\":[]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(Map.of("apiKey", "k")).build();

    SessionListConnectorFunction function =
        new SessionListConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);
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
