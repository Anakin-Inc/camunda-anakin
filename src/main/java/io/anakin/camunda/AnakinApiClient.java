package io.anakin.camunda;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal HTTP client for the Anakin API (https://api.anakin.io/v1).
 *
 * <p>Plain Java on purpose -- no Camunda Connector SDK type appears in this class' signature, so
 * it can be unit tested (see {@code AnakinApiClientTest}) without any part of the Connector
 * Runtime on the classpath. It is shared by the three connector functions in this project for
 * request/response plumbing: the {@code X-API-Key} auth header, JSON (de)serialization, and job
 * polling for the two asynchronous Anakin endpoints ({@code /url-scraper} and {@code
 * /agentic-search}).
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
   * Poll cadence and ceiling for the two job-based endpoints (url-scraper, agentic-search).
   * Matches this session's Appian plug-in build (same bounded-blocking-poll design, see
   * SUBMIT.md) and the same order of magnitude as AWS Textract's real, currently-shipping
   * Camunda "POLLING" connector mode (10 attempts, fixed delay, blocking inside execute()) --
   * scaled down because Anakin scrape/agentic-search jobs are page-fetch/AI-search jobs, not
   * OCR jobs, and complete in seconds to low tens of seconds in normal operation, not minutes.
   */
  static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
  static final Duration POLL_TIMEOUT = Duration.ofMinutes(5);

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
   * Polls {@code GET path} (an Anakin job-status endpoint) until the job's {@code status} field
   * reaches a terminal value ({@code "completed"} or {@code "failed"}), or until {@link
   * #POLL_TIMEOUT} elapses.
   *
   * <p>This blocks the calling thread -- see the design note in {@code SUBMIT.md} for why that is
   * the deliberate choice here (mirrors AWS Textract's real, currently-shipping "POLLING"
   * connector mode) rather than returning a {@code ConnectorRetryException} to let the Zeebe
   * engine re-invoke the job later. Callers (the connector functions) must ensure the BPMN service
   * task / Connector Runtime job-activation timeout exceeds {@link #POLL_TIMEOUT}, or Zeebe may
   * reassign the job to another worker mid-poll.
   *
   * @throws AnakinApiException if the request fails, the response is malformed, or the job does
   *     not reach a terminal state before the poll timeout -- callers must catch this and
   *     translate it into a {@code ConnectorException}, never let it propagate as a raw checked
   *     exception.
   */
  Map<String, Object> pollUntilTerminal(String path) throws AnakinApiException {
    long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
    Object lastStatus = null;
    while (true) {
      Map<String, Object> result = get(path);
      lastStatus = result.get("status");
      if ("completed".equals(lastStatus) || "failed".equals(lastStatus)) {
        return result;
      }
      if (System.currentTimeMillis() >= deadline) {
        throw new AnakinApiException(
            "Timed out after " + POLL_TIMEOUT.toMinutes() + " minute(s) waiting for " + path
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
