package io.anakin.camunda;

import java.util.Map;

/**
 * Shared error-message extraction for Wire job results ({@code GET /wire/jobs/:jobId}, polled by
 * {@link WireReadActionConnectorFunction} / {@link WireWriteActionConnectorFunction}).
 *
 * <p>Unlike the flat {@code {"error": "message string"}} shape every other Anakin job endpoint in
 * this project uses (url-scraper, map, crawl, agentic-search), a failed Wire job's {@code error}
 * field is itself a structured object -- {@code WireJobError { code?, message?, details?,
 * connect_url? }} per {@code anakin-mcp/src/client.ts} -- so it needs its own extraction instead
 * of the plain {@code error.toString()} the other connectors use.
 */
final class WireErrors {

  private WireErrors() {}

  /** Extracts a human-readable message from a Wire job's {@code error} field, tolerating both the
   * structured {@code {code, message, ...}} object and a plain string, and falling back to a
   * generic message when absent entirely. */
  @SuppressWarnings("unchecked")
  static String describe(Object error) {
    if (error == null) {
      return "Wire job failed with no error detail.";
    }
    if (error instanceof Map) {
      Map<String, Object> errorMap = (Map<String, Object>) error;
      Object message = errorMap.get("message");
      if (message != null) {
        Object code = errorMap.get("code");
        return code != null ? message + " (" + code + ")" : message.toString();
      }
      Object code = errorMap.get("code");
      if (code != null) {
        return "Wire job failed: " + code;
      }
      return errorMap.toString();
    }
    return error.toString();
  }
}
