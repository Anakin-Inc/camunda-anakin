package io.anakin.camunda;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal HTTP client for the Anakin API (https://api.anakin.io/v1).
 *
 * <p>Plain Java on purpose -- no Camunda Connector SDK type appears in this class' signature, so
 * it can be unit tested (see {@code AnakinApiClientTest}) without any part of the Connector
 * Runtime on the classpath. It is shared by all 21 connector functions in this project for
 * request/response plumbing: the {@code X-API-Key} auth header, JSON (de)serialization, query
 * string construction, and job polling for Anakin's asynchronous endpoints ({@code /url-scraper},
 * {@code /map}, {@code /crawl}, {@code /agentic-search}, {@code /wire/task}, {@code
 * /ai-visibility/search}, {@code /ai/evaluate}).
 *
 * <p>Uses {@link java.net.http.HttpClient} (built into the JDK) rather than adding a third-party
 * HTTP dependency to a jar that already relies on the Connector Runtime supplying Jackson/SLF4J at
 * "provided" scope.
 */
final class AnakinApiClient {

  static final String DEFAULT_BASE_URL = "https://api.anakin.io/v1";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Poll cadence and default ceiling for Anakin's job-based endpoints (url-scraper, map, crawl,
   * agentic-search, wire/task, ai-visibility/search). Matches this session's Appian plug-in build
   * (same bounded-blocking-poll design, see SUBMIT.md) and the same order of magnitude as AWS
   * Textract's real, currently-shipping Camunda "POLLING" connector mode (10 attempts, fixed
   * delay, blocking inside execute()) -- scaled down because most Anakin jobs are page-fetch/
   * AI-search jobs, not OCR jobs, and complete in seconds to low tens of seconds in normal
   * operation, not minutes.
   */
  static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
  static final Duration POLL_TIMEOUT = Duration.ofMinutes(5);

  /**
   * Wider ceiling for {@code browser_task} ({@code POST /ai/evaluate}) only: the real API
   * hard-caps a browser-AI run server-side at ~330s (5.5 minutes) -- see {@code
   * anakin-mcp/src/client.ts}'s {@code BROWSER_TASK_POLL_MAX_ATTEMPTS} comment -- so the default
   * 5-minute {@link #POLL_TIMEOUT} would risk cutting off a run that is still legitimately in
   * progress server-side. Used by {@code BrowserTaskConnectorFunction} via the {@link
   * #pollUntilTerminal(String, Duration)} overload.
   */
  static final Duration BROWSER_TASK_POLL_TIMEOUT = Duration.ofMinutes(6);

  private final String baseUrl;
  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  AnakinApiClient(String apiKey) {
    this(apiKey, DEFAULT_BASE_URL);
  }

  /** Overload taking an explicit base URL so tests can point this client at a local mock server. */
  AnakinApiClient(String apiKey, String baseUrl) {
    this(apiKey, baseUrl, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  /** Package-private constructor used by tests to also inject a custom {@link HttpClient}. */
  AnakinApiClient(String apiKey, String baseUrl, HttpClient httpClient) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();
  }

  /** POSTs {@code body} as JSON to {@code baseUrl + path}; returns the parsed JSON response. */
  Map<String, Object> post(String path, Map<String, Object> body) throws AnakinApiException {
    String json;
    try {
      json = objectMapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new AnakinApiException("Failed to serialize Anakin request body for " + path, e);
    }
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "application/json")
        .header("X-API-Key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    return send(request);
  }

  /** GETs {@code baseUrl + path}; returns the parsed JSON response. */
  Map<String, Object> get(String path) throws AnakinApiException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .timeout(REQUEST_TIMEOUT)
        .header("X-API-Key", apiKey)
        .GET()
        .build();
    return send(request);
  }

  /**
   * DELETEs {@code baseUrl + path}; returns the parsed JSON response. Used by {@code
   * session_delete} ({@code DELETE /sessions/:id}) and {@code monitor_control}'s {@code delete}
   * action ({@code DELETE /monitors/:id}) -- the only two Anakin endpoints in this project's scope
   * that use the DELETE verb, per {@code anakin-mcp/src/client.ts}'s {@code sessionDelete} /
   * {@code monitorControl}.
   */
  Map<String, Object> delete(String path) throws AnakinApiException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .timeout(REQUEST_TIMEOUT)
        .header("X-API-Key", apiKey)
        .DELETE()
        .build();
    return send(request);
  }

  /**
   * Appends a query string built from the given parameters, URL-encoding each value and skipping
   * any whose value is {@code null} or empty -- mirrors the {@code withQuery} helper in {@code
   * anakin-mcp/src/client.ts} exactly (same drop-if-empty semantics), reimplemented here in plain
   * Java rather than shared across languages. Iteration order of {@code params} is preserved in
   * the resulting query string, so callers pass a {@link java.util.LinkedHashMap} when a
   * deterministic order matters (e.g. for tests asserting on the request URI).
   */
  static String withQuery(String path, Map<String, String> params) {
    StringBuilder query = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      String value = entry.getValue();
      if (value == null || value.isEmpty()) {
        continue;
      }
      query.append(query.length() == 0 ? '?' : '&');
      query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      query.append('=');
      query.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
    return path + query;
  }

  /**
   * URL-encodes a single path segment (a slug/id interpolated into a path template, e.g. {@code
   * "/wire/catalog/" + encodePathSegment(slug)}) -- the path-segment equivalent of {@code
   * encodeURIComponent} used throughout {@code anakin-mcp/src/client.ts} for the same purpose
   * (e.g. {@code `/monitors/${encodeURIComponent(id)}`}). {@link URLEncoder} is form/query
   * encoding, not path encoding, so its {@code '+'} for spaces is corrected to the path-correct
   * {@code "%20"} here.
   */
  static String encodePathSegment(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Polls {@code GET path} (an Anakin job-status endpoint) until the job's {@code status} field
   * reaches a terminal value, or until {@link #POLL_TIMEOUT} elapses. Equivalent to {@code
   * pollUntilTerminal(path, POLL_TIMEOUT)}.
   *
   * @see #pollUntilTerminal(String, Duration)
   */
  Map<String, Object> pollUntilTerminal(String path) throws AnakinApiException {
    return pollUntilTerminal(path, POLL_TIMEOUT);
  }

  /**
   * Polls {@code GET path} until the job's {@code status} field reaches a terminal value ({@code
   * "completed"}, {@code "failed"}, or {@code "timed_out"} -- the last one is only ever emitted by
   * {@code GET /ai/jobs/:workflowId}, browser_task's poll endpoint, per {@code
   * anakin-mcp/src/client.ts}'s {@code browserTask}), or until {@code timeout} elapses.
   *
   * <p>This blocks the calling thread -- see the design note in {@code SUBMIT.md} for why that is
   * the deliberate choice here (mirrors AWS Textract's real, currently-shipping "POLLING"
   * connector mode) rather than returning a {@code ConnectorRetryException} to let the Zeebe
   * engine re-invoke the job later. Callers (the connector functions) must ensure the BPMN service
   * task / Connector Runtime job-activation timeout exceeds {@code timeout}, or Zeebe may reassign
   * the job to another worker mid-poll.
   *
   * @throws AnakinApiException if the request fails, the response is malformed, or the job does
   *     not reach a terminal state before the poll timeout -- callers must catch this and
   *     translate it into a {@code ConnectorException}, never let it propagate as a raw checked
   *     exception.
   */
  Map<String, Object> pollUntilTerminal(String path, Duration timeout) throws AnakinApiException {
    return pollUntilTerminal(path, timeout, AnakinApiClient::isTerminalStatus);
  }

  /**
   * Polls {@code GET path} until {@code isTerminal} accepts the job's {@code status} field, or
   * until {@code timeout} elapses. General form of {@link #pollUntilTerminal(String, Duration)}
   * for the one endpoint in this project whose terminal-state rule is not "one of a fixed literal
   * set": {@code ai_visibility_search}'s {@code GET /ai-visibility/search/:search_id} is terminal
   * on <em>any</em> status other than {@code "running"} (per {@code anakin-mcp/src/client.ts}'s
   * {@code aiVisibilitySearch}: {@code if (search.status !== 'running') return search}), not just
   * {@code "completed"}/{@code "failed"}.
   *
   * @see #pollUntilTerminal(String, Duration)
   */
  Map<String, Object> pollUntilTerminal(String path, Duration timeout, Predicate<Object> isTerminal)
      throws AnakinApiException {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    Object lastStatus = null;
    while (true) {
      Map<String, Object> result = get(path);
      lastStatus = result.get("status");
      if (isTerminal.test(lastStatus)) {
        return result;
      }
      if (System.currentTimeMillis() >= deadline) {
        throw new AnakinApiException(
            "Timed out after " + timeout.toMinutes() + " minute(s) waiting for " + path
                + " to reach a terminal state (last status: " + lastStatus + ")",
            (Throwable) null);
      }
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new AnakinApiException("Interrupted while polling " + path, ie);
      }
    }
  }

  private static boolean isTerminalStatus(Object status) {
    return "completed".equals(status) || "failed".equals(status) || "timed_out".equals(status);
  }

  private Map<String, Object> send(HttpRequest request) throws AnakinApiException {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new AnakinApiException("Network error calling Anakin API (" + request.uri() + "): " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AnakinApiException("Interrupted calling Anakin API (" + request.uri() + ")", e);
    }

    Map<String, Object> body = parseJson(response.body());
    if (response.statusCode() >= 400) {
      Object apiMessage = body.get("error");
      String message = apiMessage != null
          ? apiMessage.toString()
          : "Anakin API returned HTTP " + response.statusCode() + " with no error detail";
      throw new AnakinApiException(message, response.statusCode(), body);
    }
    return body;
  }

  private Map<String, Object> parseJson(String json) throws AnakinApiException {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
    } catch (IOException e) {
      throw new AnakinApiException("Anakin API returned malformed JSON: " + e.getMessage(), e);
    }
  }

  /** Extracts the job id from a submit response, tolerating jobId / job_id / id keys. */
  static String requireJobId(Map<String, Object> submitted) throws AnakinApiException {
    Object jobId = submitted.get("jobId");
    if (jobId == null) {
      jobId = submitted.get("job_id");
    }
    if (jobId == null) {
      jobId = submitted.get("id");
    }
    if (!(jobId instanceof String) || ((String) jobId).isEmpty()) {
      throw new AnakinApiException(
          "Anakin API response did not include a job id: " + submitted, (Throwable) null);
    }
    return (String) jobId;
  }

  /**
   * Like {@link #requireJobId} but returns {@code null} instead of throwing when no job id is
   * present, rather than {@code jobId}/{@code id} -- used by {@code POST /wire/task}
   * ({@code WireReadActionConnectorFunction} / {@code WireWriteActionConnectorFunction}), whose
   * response is {@code WireTaskAccepted { job_id? }} in {@code anakin-mcp/src/client.ts}: some
   * Wire actions are synchronous and return their terminal {@code data} inline with no job id at
   * all, in which case the submit response itself is already the final result and must not be
   * polled.
   */
  static String optionalJobId(Map<String, Object> submitted) {
    Object jobId = submitted.get("job_id");
    return (jobId instanceof String && !((String) jobId).isEmpty()) ? (String) jobId : null;
  }

  /**
   * Checked exception wrapping every way an Anakin API call can fail: network/timeout errors,
   * HTTP error responses, malformed JSON, and poll-timeout. Deliberately checked -- connector
   * functions must catch it and translate it into a {@code io.camunda.connector.api.error.
   * ConnectorException} rather than letting a raw checked exception surface (the
   * {@code OutboundConnectorFunction#execute} contract permits throwing any checked exception and
   * the runtime will catch it, but that loses the structured error code a {@code
   * ConnectorException} carries into the BPMN error boundary / {@code errorExpression}).
   */
  static final class AnakinApiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Integer statusCode;
    private final transient Map<String, Object> responseBody;

    AnakinApiException(String message, Throwable cause) {
      super(message, cause);
      this.statusCode = null;
      this.responseBody = null;
    }

    AnakinApiException(String message, Integer statusCode, Map<String, Object> responseBody) {
      super(message);
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    Integer getStatusCode() {
      return statusCode;
    }

    Map<String, Object> getResponseBody() {
      return responseBody;
    }
  }
}
