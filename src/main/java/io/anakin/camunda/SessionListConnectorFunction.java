package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.SessionListRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists saved browser sessions (encrypted login states) via Anakin's {@code GET
 * /v1/sessions?domain=}. Synchronous -- no job id, no polling. Request/response shapes mirror
 * {@code sessionListTool} / {@code AnakinClient.sessionsList} in {@code
 * anakin-mcp/src/tools/sessions.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Each returned session's {@code id} is what other connectors take as {@code sessionId} (Url
 * Scraper, Crawl, Create Monitor, Browser Task) to work with login-protected pages. Creating a
 * session is not exposed by this project -- it is an interactive flow (a live browser the user
 * logs into, completing 2FA/captchas themselves) that cannot run inside a Connector Runtime job,
 * same limitation the real MCP tool documents in {@code sessions.ts}'s header comment.
 */
@OutboundConnector(
    name = "Anakin - List Sessions",
    inputVariables = {"apiKey", "domain"},
    type = "io.anakin:session-list:1")
@ElementTemplate(
    id = "io.anakin.connector.SessionList.v1",
    name = "Anakin - List Sessions",
    version = 1,
    description = "List saved browser sessions with Anakin (https://anakin.io). Synchronous --"
        + " no polling.",
    inputDataClass = SessionListRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Filter")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class SessionListConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(SessionListConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public SessionListConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  SessionListConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    SessionListRequest request = context.bindVariables(SessionListRequest.class);

    Map<String, String> query = new LinkedHashMap<>();
    query.put("domain", request.domain());

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.get(AnakinApiClient.withQuery("/sessions", query));
    } catch (AnakinApiException e) {
      LOG.error("Anakin sessions (list) request failed for domain={} statusCode={}: {}",
          request.domain(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
