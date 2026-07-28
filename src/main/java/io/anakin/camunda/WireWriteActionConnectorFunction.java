package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.WireWriteActionRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a Wire WRITE action -- one whose {@code type} is {@code "write"} (performs a
 * state-changing interaction on the target site: submit a form, add to a cart, post/send
 * content, update settings) -- via Anakin's {@code POST /v1/wire/task {action_id, params?,
 * credential_id?, identity_id?}}. Some actions return their result inline (synchronous); others
 * return a {@code job_id} that this connector polls at {@code GET /v1/wire/jobs/:jobId} to a
 * terminal state. Request/response shapes mirror {@code wireWriteActionTool} / {@code
 * AnakinClient.wireRun} in {@code anakin-mcp/src/tools/wire.ts} / {@code
 * anakin-mcp/src/client.ts} exactly -- request shape is byte-for-byte identical to {@link
 * WireReadActionConnectorFunction}, kept as a distinct connector/element template so a BPMN
 * modeler picking this element is knowingly choosing a state-changing action, not a read.
 *
 * <p>Discover {@code action_id}s first with {@link WireDiscoverConnectorFunction} or {@link
 * WireCatalogConnectorFunction} and confirm the action's type is {@code "write"}; for read-only
 * data extraction use {@link WireReadActionConnectorFunction} instead. Most write actions need
 * auth -- pass a {@code credentialId} from {@link WireIdentitiesConnectorFunction} or {@link
 * WireLoginConnectorFunction}.
 *
 * <p>This connector does not itself refuse payment/fund-transfer action ids -- the real MCP tool
 * this mirrors applies that as a client-side policy guard (see {@code
 * anakin-mcp/src/tools/policy.ts}'s {@code financialBlockReason}), which is a directory-listing
 * policy for an autonomous LLM agent loop, not part of the Anakin REST API contract this
 * connector wraps. Not reimplemented here -- see {@code SUBMIT.md}.
 */
@OutboundConnector(
    name = "Anakin - Wire Write Action",
    inputVariables = {"apiKey", "actionId", "params", "credentialId", "identityId"},
    type = "io.anakin:wire-write-action:1")
@ElementTemplate(
    id = "io.anakin.connector.WireWriteAction.v1",
    name = "Anakin - Wire Write Action",
    version = 1,
    description = "Run a Wire WRITE (state-changing) action with Anakin (https://anakin.io) --"
        + " submits a form, adds to a cart, etc. Transparently polls to completion when the"
        + " action is asynchronous.",
    inputDataClass = WireWriteActionRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "input", label = "Action input"),
      @PropertyGroup(id = "auth", label = "Action credential (optional)")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class WireWriteActionConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(WireWriteActionConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public WireWriteActionConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  WireWriteActionConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    WireWriteActionRequest request = context.bindVariables(WireWriteActionRequest.class);

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
      LOG.error("Anakin wire/task (write) request failed for actionId={} statusCode={}: {}",
          request.actionId(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }
}
