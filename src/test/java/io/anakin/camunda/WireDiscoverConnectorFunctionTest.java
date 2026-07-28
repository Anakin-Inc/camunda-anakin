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

/** Connector-level test for the synchronous {@code /wire/resolve} endpoint -- no job id, no polling. */
class WireDiscoverConnectorFunctionTest {

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
  void executeSendsQAndLimitAsQueryParams() throws Exception {
    server.createContext("/wire/resolve", exchange -> {
      assertThat(exchange.getRequestMethod()).isEqualTo("GET");
      assertThat(exchange.getRequestURI().getQuery()).isEqualTo("q=top+phones+on+walmart&limit=3");
      writeJson(exchange, 200, "{\"results\":[{\"action_id\":\"walmart.search_products\"}]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "q", "top phones on walmart", "limit", 3))
            .build();

    WireDiscoverConnectorFunction function =
        new WireDiscoverConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsKey("results");
  }

  @Test
  void executeOmitsLimitFromQueryWhenNotSupplied() throws Exception {
    server.createContext("/wire/resolve", exchange -> {
      assertThat(exchange.getRequestURI().getQuery()).isEqualTo("q=airbnb+listings");
      writeJson(exchange, 200, "{\"results\":[]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "q", "airbnb listings"))
            .build();

    WireDiscoverConnectorFunction function =
        new WireDiscoverConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    function.execute(context);
  }

  @Test
  void executeThrowsConnectorExceptionOnHttpError() throws Exception {
    server.createContext("/wire/resolve", exchange -> writeJson(exchange, 401, "{\"error\":\"Invalid API key\"}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "bad-key", "q", "top phones"))
            .build();

    WireDiscoverConnectorFunction function =
        new WireDiscoverConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessage("Invalid API key")
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
