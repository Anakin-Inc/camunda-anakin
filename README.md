# camunda-anakin

Camunda 8 Outbound Connectors for [Anakin](https://anakin.io) -- web
scraping/crawling/mapping, AI-powered search, multi-stage agentic research,
Wire site automation, website change monitoring, AI-visibility comparison,
browser sessions, and AI browser automation, via `https://api.anakin.io/v1`.
Covers all 21 of Anakin's REST API capabilities, one `OutboundConnectorFunction`
class and element template per capability. Built against the real, open-source
[Camunda Connector SDK](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/)
(`io.camunda.connector:connector-core`), Classic Function API style
(`OutboundConnectorFunction#execute`).

## Install

Build the connector jar:

```sh
mvn clean package
```

This produces `target/camunda-anakin-1.0.0.jar` (uber jar, connector-core/
connector-validation/element-template-generator-annotations excluded since
the Connector Runtime supplies those) and regenerates all 21 element
templates under `element-templates/`.

Deploy the jar into a Camunda 8 Connector Runtime -- either your own image
built `FROM camunda/connectors-bundle` with this jar added under
`/opt/app`, or a Spring Boot app depending on
`io.camunda.connector:spring-boot-starter-camunda-connectors` with this jar
on the classpath. Then, in Camunda Modeler, **Element Templates > Import
element template ...** and point at each file under `element-templates/` so
the connectors you need appear in the palette for BPMN service tasks.

## Usage

Each connector is a separate element template, matching how each Anakin
endpoint is its own operation with a distinct request/response shape and
its own sync/async behavior:

| Connector | Anakin endpoint(s) | Notes |
|---|---|---|
| **Anakin - Scrape URL** | `POST /url-scraper`, poll `GET /url-scraper/:jobId` | Blocks the job worker while polling (bounded, see [SUBMIT.md](SUBMIT.md)). `Formats` (FEEL list, default `["markdown"]`) selects which of markdown/html/cleanedHtml/json/links/images/screenshot/screenshotFullPage/summary come back. |
| **Anakin - AI Search** | `POST /search` | Synchronous -- no polling. Costs 3 credits/call. |
| **Anakin - Agentic Search** | `POST /agentic-search`, poll `GET /agentic-search/:jobId` | Multi-stage research with optional JSON Schema for structured output. Costs 10 credits/call. Blocks while polling. |
| **Anakin - Map Site** | `POST /map`, poll `GET /map/:jobId` | Discovers reachable URLs under a site (depth/limit-bounded). Blocks while polling. |
| **Anakin - Crawl Site** | `POST /crawl`, poll `GET /crawl/:jobId` | Bulk-fetches markdown across a site (`includePatterns`/`excludePatterns`, saved-session support). Blocks while polling. |
| **Anakin - Wire Discover** | `GET /wire/resolve?q=&limit=` | Natural-language intent to ranked candidate Wire actions. Synchronous. |
| **Anakin - Wire Catalog** | `GET /wire/catalog` or `GET /wire/catalog/:slug` | Lists every Wire catalog, or one catalog's full action list. Synchronous. |
| **Anakin - Wire Read Action** | `POST /wire/task`, poll `GET /wire/jobs/:jobId` (when async) | Runs a read-type (data-extraction) Wire action. Some actions return inline with no polling. |
| **Anakin - Wire Write Action** | `POST /wire/task`, poll `GET /wire/jobs/:jobId` (when async) | Runs a write-type (state-changing) Wire action. Same request shape as Wire Read Action, separate element template so a modeler knowingly picks a state-changing element. |
| **Anakin - Wire Identities** | `GET /wire/identities?catalog_id=` | Lists saved Wire identities/credentials. Synchronous. |
| **Anakin - Wire Login** | `POST /wire/login` | Signs in to a credentials-mode Wire site, returns a `credential_id`. Synchronous. |
| **Anakin - Wire Build** | `POST /wire/build-request` | Requests a brand-new Wire action for an uncatalogued site. Returns `status: "pending"`; no further polling endpoint in scope. |
| **Anakin - Create Monitor** | `POST /monitors` | Creates a scheduled website/site/Wire-action monitor. Only sends fields the caller actually set (see `MonitorCreateConnectorFunction` Javadoc) -- the API applies its own server-side defaults for the rest. Synchronous. |
| **Anakin - List Monitors** | `GET /monitors` or `GET /monitors/:id` | Lists monitors, or fetches one by id. Synchronous. |
| **Anakin - Monitor Changes** | `GET /monitors/:id/changes` | Detected changes for a monitor. Synchronous. |
| **Anakin - Control Monitor** | `POST /monitors/:id/{pause,resume,run}` or `DELETE /monitors/:id` | Pause/resume/run-now/delete, selected by an `action` field. Synchronous. |
| **Anakin - AI Visibility Search** | `POST /ai-visibility/search`, poll `GET /ai-visibility/search/:search_id` | Compares multiple AI answer engines' responses to one query. Terminal on any status other than `"running"` (not a fixed literal set) -- a `"failed"` result is returned, not thrown, since it can carry partial per-source data. |
| **Anakin - AI Visibility Sources** | `GET /ai-visibility/sources` | Lists available AI answer engines. Synchronous, no input beyond auth. |
| **Anakin - List Sessions** | `GET /sessions?domain=` | Lists saved browser sessions (encrypted login states). Synchronous. |
| **Anakin - Delete Session** | `DELETE /sessions/:id` | Permanently deletes a saved browser session. Irreversible. Synchronous. |
| **Anakin - Browser Task** | `POST /ai/evaluate` (`async: true`), poll `GET /ai/jobs/:workflowId` | Natural-language AI browser automation. Wider poll ceiling than the other connectors (6 min vs. 5) since the real API hard-caps a run at ~330s server-side; `GET /ai/jobs/:workflowId` can also report a `"timed_out"` terminal status distinct from `"failed"`. |

On the service task's **Authentication** group, paste a secret reference
(e.g. `{{secrets.ANAKIN_API_KEY}}`) into the **Anakin API Key** field --
never a raw key. Configure the `ANAKIN_API_KEY` secret in your Connector
Runtime / Zeebe cluster's secret store. Get a free API key (300 credits, no
card required) at [anakin.io/dashboard](https://anakin.io/dashboard).

Every connector returns the Anakin API's JSON response as-is (a
`Map<String,Object>`, or occasionally a nested value pulled out of it --
e.g. Browser Task returns `job.result` when present), so the service task's
**Result variable** / **Result expression** (FEEL) can pull whatever fields
it needs without a fixed output schema.

On failure, each connector throws a
`io.camunda.connector.api.error.ConnectorException` with one of these
`errorCode`s, catchable from an **Error expression** or a BPMN error
boundary event on the service task:

- `ANAKIN_API_ERROR` -- network/HTTP/malformed-response failure.
- `ANAKIN_JOB_FAILED` -- the Anakin job itself reached `status: "failed"`.
- `ANAKIN_JOB_TIMED_OUT` -- Browser Task only: the job reached
  `status: "timed_out"`, a distinct terminal state from `"failed"`.
- `ANAKIN_INVALID_INPUT` -- Control Monitor only: an unrecognized `action`
  value, caught client-side before any request is sent.

## Test

```sh
mvn test
```

Runs JUnit 5 (`AnakinApiClientTest` + one connector-level test class per
connector, 22 test classes total) against a local
`com.sun.net.httpserver.HttpServer` mock -- no live Anakin API key or
network access required. See [SUBMIT.md](SUBMIT.md) for exactly what this
does and does not verify.

## Build

```sh
mvn clean package
```

Compiles against the real Camunda Connector SDK
(`io.camunda.connector:connector-core`, `connector-validation`,
`element-template-generator-annotations`, all published on Maven Central),
runs tests, assembles the uber jar via `maven-shade-plugin`, and
regenerates `element-templates/*.json` via
`element-template-generator-maven-plugin`.
