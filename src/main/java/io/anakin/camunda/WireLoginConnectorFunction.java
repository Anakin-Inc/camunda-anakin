package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireLoginRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signs in to a credentials-mode Wire site via Anakin's {@code POST /v1/wire/login {catalog_slug,
 * identity_name?, params?, source_id?, source_ref?}}, returning a {@code credential_id} usable
 * immediately with {@link WireReadActionConnectorFunction} / {@link
 * WireWriteActionConnectorFunction}. Synchronous -- no job id, no polling. Request/response
 * shapes mirror {@code wireLoginTool} / {@code AnakinClient.wireLogin} in {@code
 * anakin-mcp/src/tools/wire.ts} / {@code anakin-mcp/src/client.ts} exactly.
 *
 * <p>Only needed for actions whose {@code auth_mode} is {@code "required"}, and only for catalogs
 * that support password sign-in; cookie-based sites use the dashboard connect flow instead. The
 * password itself is never stored by Anakin, only the resulting encrypted session -- but it does
 * pass through this connector's input variables and the BPMN execution history, so the {@code
 * Login Params} field should itself hold a secret reference (e.g. {@code
 * {{secrets.SITE_PASSWORD}}}) wherever the process definition allows it, the same operational
 * caution as any other Camunda connector taking a raw credential as an input variable.
 */
@OutboundConnector(
    name = "Anakin - Wire Login",
    inputVariables = {"apiKey", "catalogSlug", "params", "identityName", "sourceId", "sourceRef"},
    type = "io.anakin:wire-login:1")
@ElementTemplate(
    id = "io.anakin.connector.WireLogin.v1",
    name = "Anakin - Wire Login",
    version = 1,
    description = "Sign in to a credentials-mode Wire site with Anakin (https://anakin.io) and"
        + " get a credential_id. Synchronous -- no polling.",
    inputDataClass = WireLoginRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Login input"),
      @PropertyGroup(id = "onepassword", label = "1Password (optional)")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireLoginConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireLoginConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireLoginConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireLoginConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireLoginRequest request = context.bindVariables(WireLoginRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("catalog_slug", request.catalogSlug());
    if (request.params() != null && !request.params().isEmpty()) {
      requestBody.put("params", request.params());
    }
    if (request.identityName() != null && !request.identityName().isBlank()) {
      requestBody.put("identity_name", request.identityName());
    }
    if (request.sourceId() != null && !request.sourceId().isBlank()) {
      requestBody.put("source_id", request.sourceId());
    }
    if (request.sourceRef() != null && !request.sourceRef().isEmpty()) {
      requestBody.put("source_ref", request.sourceRef());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.post("/wire/login", requestBody);
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/login request failed for catalogSlug={} statusCode={}: {}",
          request.catalogSlug(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
