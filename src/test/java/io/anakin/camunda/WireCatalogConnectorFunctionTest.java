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

/** Connector-level test for the synchronous {@code /wire/catalog} endpoint -- no job id, no polling. */
class WireCatalogConnectorFunctionTest {

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
  void executeListsAllCatalogsWhenSlugOmitted() throws Exception {
    server.createContext("/wire/catalog", exchange -> {
      assertThat(exchange.getRequestURI().getPath()).isEqualTo("/wire/catalog");
      writeJson(exchange, 200, "{\"catalogs\":[{\"slug\":\"walmart\"}]}");
    });
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create().variables(Map.of("apiKey", "k")).build();

    WireCatalogConnectorFunction function =
        new WireCatalogConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap).containsKey("catalogs");
  }

  @Test
  void executeFetchesOneCatalogWhenSlugSupplied() throws Exception {
    server.createContext("/wire/catalog/walmart", exchange ->
        writeJson(exchange, 200, "{\"slug\":\"walmart\",\"actions\":[]}"));
    server.start();

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .variables(Map.of("apiKey", "k", "slug", "walmart"))
            .build();

    WireCatalogConnectorFunction function =
        new WireCatalogConnectorFunction(apiKey -> new AnakinApiClient(apiKey, baseUrl));
    Object result = function.execute(context);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertThat(resultMap.get("slug")).isEqualTo("walmart");
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
