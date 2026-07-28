package io.anakin.camunda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.anakin.camunda.AnakinApiClient.AnakinApiException;
import io.anakin.camunda.model.MonitorCreateRequest;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a scheduled website monitor via Anakin's {@code POST /v1/monitors}. Synchronous -- the
 * create call itself returns the new monitor's configuration directly; no job id, no polling
 * (the monitor then runs its own checks on {@code intervalMinutes}, inspected later via {@link
 * MonitorListConnectorFunction} / {@link MonitorChangesConnectorFunction}).
 *
 * <p>Unlike every other connector in this project, the request body sent to Anakin only includes
 * the optional fields the BPMN process actually set (non-null after {@code bindVariables}) --
 * matching {@code AnakinClient.monitorCreate}'s own "only send fields the caller set" behavior in
 * {@code anakin-mcp/src/client.ts} (the API applies its own server-side defaults for anything
 * omitted, e.g. {@code scope: "page"}, {@code watchMode: "full_page"}). The {@code
 * @TemplateProperty} default values shown in Camunda Modeler for these fields are UI hints for the
 * process author, not values this connector injects itself when a field is left unset.
 */
@OutboundConnector(
    name = "Anakin - Create Monitor",
    inputVariables = {
      "apiKey", "url", "intervalMinutes", "scope", "watchMode", "watchFormat", "outputSchema",
      "aiMode", "aiGoal", "useBrowser", "country", "sessionId", "isActive", "expiresAt",
      "alertWebhookUrl", "alertEmails", "maxPages", "maxDepth", "includePatterns",
      "excludePatterns", "wireActionId", "wireCatalogSlug", "wireCredentialId", "wireParams",
      "wireWatchPaths"
    },
    type = "io.anakin:monitor-create:1")
@ElementTemplate(
    id = "io.anakin.connector.MonitorCreate.v1",
    name = "Anakin - Create Monitor",
    version = 1,
    description = "Create a scheduled website monitor with Anakin (https://anakin.io) -- checks a"
        + " URL/site/Wire action on a schedule and records changes, optionally alerting a"
        + " webhook or email.",
    inputDataClass = MonitorCreateRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "target", label = "Target"),
      @PropertyGroup(id = "watch", label = "Watch settings"),
      @PropertyGroup(id = "fetch", label = "Fetch settings"),
      @PropertyGroup(id = "schedule", label = "Schedule"),
      @PropertyGroup(id = "alerts", label = "Alerts"),
      @PropertyGroup(id = "site", label = "Site scope (optional)"),
      @PropertyGroup(id = "wire", label = "Wire scope (optional)")
    },
    engineVersion = "^8.9",
    documentationRef = "https://anakin.io/docs",
    icon = "icon.svg")
public class MonitorCreateConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(MonitorCreateConnectorFunction.class);

  private final Function<String, AnakinApiClient> clientFactory;

  public MonitorCreateConnectorFunction() {
    this(AnakinApiClient::new);
  }

  /** Package-private constructor for tests: injects a client factory pointed at a local mock server. */
  MonitorCreateConnectorFunction(Function<String, AnakinApiClient> clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    MonitorCreateRequest request = context.bindVariables(MonitorCreateRequest.class);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("url", request.url());
    requestBody.put("intervalMinutes", request.intervalMinutes());
    putIfSet(requestBody, "scope", request.scope());
    putIfSet(requestBody, "watchMode", request.watchMode());
    putIfSet(requestBody, "watchFormat", request.watchFormat());
    putIfSet(requestBody, "outputSchema", request.outputSchema());
    putIfSet(requestBody, "aiMode", request.aiMode());
    putIfSet(requestBody, "aiGoal", request.aiGoal());
    putIfSet(requestBody, "useBrowser", request.useBrowser());
    putIfSet(requestBody, "country", request.country());
    putIfSet(requestBody, "sessionId", request.sessionId());
    putIfSet(requestBody, "isActive", request.isActive());
    putIfSet(requestBody, "expiresAt", request.expiresAt());
    putIfSet(requestBody, "alertWebhookUrl", request.alertWebhookUrl());
    putIfSet(requestBody, "alertEmails", request.alertEmails());
    putIfSet(requestBody, "maxPages", request.maxPages());
    putIfSet(requestBody, "maxDepth", request.maxDepth());
    putIfSet(requestBody, "includePatterns", request.includePatterns());
    putIfSet(requestBody, "excludePatterns", request.excludePatterns());
    putIfSet(requestBody, "wireActionId", request.wireActionId());
    putIfSet(requestBody, "wireCatalogSlug", request.wireCatalogSlug());
    putIfSet(requestBody, "wireCredentialId", request.wireCredentialId());
    putIfSet(requestBody, "wireParams", request.wireParams());
    putIfSet(requestBody, "wireWatchPaths", request.wireWatchPaths());

    AnakinApiClient client = clientFactory.apply(request.apiKey());
    try {
      // Synchronous endpoint -- no job id, no polling.
      return client.post("/monitors", requestBody);
    } catch (AnakinApiException e) {
      LOG.error("Anakin monitors (create) request failed for url={} statusCode={}: {}",
          request.url(), e.getStatusCode(), e.getMessage(), e);
      throw new ConnectorException("ANAKIN_API_ERROR", e.getMessage(), e);
    }
  }

  /** Adds {@code key -> value} only when the caller actually set a non-null, non-blank value --
   * see the class Javadoc for why this connector does not apply its own client-side defaults. */
  private static void putIfSet(Map<String, Object> body, String key, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof String stringValue && stringValue.isBlank()) {
      return;
    }
    if (value instanceof java.util.Collection<?> collectionValue && collectionValue.isEmpty()) {
      return;
    }
    if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
      return;
    }
    body.put(key, value);
  }
}
