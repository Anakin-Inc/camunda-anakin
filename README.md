# camunda-anakin

Camunda 8 Outbound Connectors for [Anakin](https://anakin.io) -- web scraping,
AI-powered search, and multi-stage agentic research over hundreds of popular
sites, via `https://api.anakin.io/v1`. Built against the real, open-source
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
the Connector Runtime supplies those) and regenerates the three element
templates under `element-templates/`.

Deploy the jar into a Camunda 8 Connector Runtime -- either your own image
built `FROM camunda/connectors-bundle` with this jar added under
`/opt/app`, or a Spring Boot app depending on
`io.camunda.connector:spring-boot-starter-camunda-connectors` with this jar
on the classpath. Then, in Camunda Modeler, **Element Templates > Import
element template ...** and point at each file under `element-templates/` so
the three connectors appear in the palette for BPMN service tasks.

## Usage

Each connector is a separate element template, matching how `POST
/url-scraper`, `POST /search`, and `POST /agentic-search` are three
distinct Anakin operations with different request/response shapes and
different sync/async behavior:

| Connector | Anakin endpoint(s) | Notes |
|---|---|---|
| **Anakin - Scrape URL** | `POST /url-scraper`, poll `GET /url-scraper/:jobId` | Blocks the job worker while polling (bounded, see [SUBMIT.md](SUBMIT.md)). `Formats` (FEEL list, default `["markdown"]`) selects which of markdown/html/cleanedHtml/json/links/images/screenshot/screenshotFullPage/summary come back. |
| **Anakin - AI Search** | `POST /search` | Synchronous -- no polling. Costs 3 credits/call. |
| **Anakin - Agentic Search** | `POST /agentic-search`, poll `GET /agentic-search/:jobId` | Multi-stage research with optional JSON Schema for structured output. Costs 10 credits/call. Blocks while polling, same as Scrape URL. |

On the service task's **Authentication** group, paste a secret reference
(e.g. `{{secrets.ANAKIN_API_KEY}}`) into the **Anakin API Key** field --
never a raw key. Configure the `ANAKIN_API_KEY` secret in your Connector
Runtime / Zeebe cluster's secret store. Get a free API key (300 credits, no
card required) at [anakin.io/dashboard](https://anakin.io/dashboard).

Every connector returns the Anakin API's JSON response as-is (a
`Map<String,Object>`), so the service task's **Result variable** / **Result
expression** (FEEL) can pull whatever fields it needs (`markdown`,
`results`, `generatedJson`, etc.) without a fixed output schema.

On failure, each connector throws a
`io.camunda.connector.api.error.ConnectorException` with an `errorCode` of
either `ANAKIN_API_ERROR` (network/HTTP/malformed-response failure) or
`ANAKIN_JOB_FAILED` (the Anakin job itself reached `status: "failed"`) --
catchable from an **Error expression** or a BPMN error boundary event on the
service task.

## Test

```sh
mvn test
```

Runs JUnit 5 (`AnakinApiClientTest` + one connector-level test class per
connector) against a local `com.sun.net.httpserver.HttpServer` mock -- no
live Anakin API key or network access required. See
[SUBMIT.md](SUBMIT.md) for exactly what this does and does not verify.

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
