package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireReadActionRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a Wire READ action -- one whose {@code type} is {@code "read"} (extracts data, no state
 * change on the target site) -- via Anakin's {@code POST /v1/wire/task {action_id, params?,
 * credential_id?, identity_id?}}. Some actions return their result inline (synchronous); others
 * return a {@code job_id} that this connector polls at {@code GET /v1/wire/jobs/:jobId} to a
 * terminal state. Request/response shapes mirror {@code wireReadActionTool} / {@code
 * AnakinClient.wireRun} in {@code anakin-mcp/src/tools/wire.ts} / {@code
 * anakin-mcp/src/client.ts} exactly.
 *
 * <p>Discover {@code action_id}s first with {@link WireDiscoverConnectorFunction} or {@link
 * WireCatalogConnectorFunction} and confirm the action's type is {@code "read"}; for
 * state-changing actions ({@code "write"}) use {@link WireWriteActionConnectorFunction} instead.
 * Most read actions need no auth; when the action's {@code auth_mode} is {@code "required"}, pass
 * a {@code credentialId} from {@link WireIdentitiesConnectorFunction} or {@link
 * WireLoginConnectorFunction}.
 *
 * <p>Blocks the job worker thread inside {@link #execute} until the Wire job reaches a terminal
 * state or {@link AnakinApiClient#POLL_TIMEOUT} elapses, same as {@link
 * UrlScraperConnectorFunction} -- but only when the action is actually asynchronous; synchronous
 * actions return immediately with no polling at all.
 */
@OutboundConnector(
    name = "Anakin - Wire Read Action",
    inputVariables = {"apiKey", "actionId", "params", "credentialId", "identityId"},
    type = "io.anakin:wire-read-action:1")
@ElementTemplate(
    id = "io.anakin.connector.WireReadAction.v1",
    name = "Anakin - Wire Read Action",
    version = 1,
    description = "Run a Wire READ (data-extraction, non-destructive) action with Anakin"
        + " (https://anakin.io). Transparently polls to completion when the action is"
        + " asynchronous.",
    inputDataClass = WireReadActionRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Action input"),
      @PropertyGroup(id = "auth", label = "Action credential (optional)")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireReadActionConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireReadActionConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireReadActionConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireReadActionConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireReadActionRequest request = context.bindVariables(WireReadActionRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("action_id", request.actionId());
    if (request.params() != null && !request.params().isEmpty()) {
      requestBody.put("params", request.params());
    }
    if (request.credentialId() != null && !request.credentialId().isBlank()) {
      requestBody.put("credential_id", request.credentialId());
    }
    if (request.identityId() != null && !request.identityId().isBlank()) {
      requestBody.put("identity_id", request.identityId());
    }

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      Map<String, Object> accepted = client.post("/wire/task", requestBody);
      String jobId = AnakinApiClient.optionalJobId(accepted);
      // A synchronous action already carries its terminal data inline -- no job id, no polling.
      Map<String, Object> result = jobId != null
          ? client.pollUntilTerminal("/wire/jobs/" + AnakinApiClient.encodePathSegment(jobId))
          : accepted;

      if ("failed".equals(result.get("status"))) {
        throw new ConnectorException("ANAKIN_JOB_FAILED", WireErrors.describe(result.get("error")));
      }
      return result;
    } catch (AnakinApiException e) {
      LOG.error("Anakin wire/task (read) request failed for actionId={} statusCode={}: {}",
          request.actionId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
