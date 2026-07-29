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

class WireLoginConnectorFunctionTest {

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
  void executeReturnsCredentialOnSuccess() throws Exception {
    server.createContext("/wire/login", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("POST");
      String body = new String(exchange.getRequestBody().readAllBytes());
      assertThat(body).contains("\"catalog_slug\":\"neb\"");
      writeJson(exchange, 200, "{\"credential_id\":\"cred-42\"}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "catalogSlug", "neb"))
            .build();

    WireLoginConnectorFunction function =
        new WireLoginConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("credential_id")).isEqualTo("cred-42");
  }

  @Test
  void executeThrowsConnectorExceptionOnHttpError() throws Exception {
    server.createContext("/wire/login", exchange -> writeJson(exchange, 401, "{\"error\":\"bad credentials\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "catalogSlug", "neb"))
            .build();

    WireLoginConnectorFunction function =
        new WireLoginConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("bad credentials")
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
