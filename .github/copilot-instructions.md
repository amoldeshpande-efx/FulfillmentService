# FulfillmentService — Copilot Instructions

## Project Overview

Spring Boot 3.2.3 MCP server (Java 21) for Equifax EWS VS ICI fulfillment.
Dual interface: REST API + MCP (Model Context Protocol) over JSON-RPC 2.0.
Persists to Google Cloud Datastore, publishes events to Pub/Sub.

**GCP Project:** `ews-vs-icie-dev-npe-0bb5`
**Team:** EWS VS ICI
**Package root:** `com.equifax.ews.vs.ici`

## Tech Stack

- Java 21, Spring Boot 3.2.3, Spring Cloud GCP 5.0.0
- Google Cloud Datastore (native client, not Spring Data)
- Google Cloud Pub/Sub (via Spring `PubSubTemplate`)
- MCP protocol: custom implementation (McpController, McpToolRegistry, McpTool interface)
- Springdoc OpenAPI 2.4.0 (Swagger UI at `/swagger-ui.html`)
- Logstash Logback Encoder 7.4 for structured JSON logging
- Lombok for boilerplate reduction
- Cloud Functions Gen 1 adapter (`FulfillmentFunction.java`) for GCP deployment

## Architecture

```
LLM/Agent ──MCP──▶ McpController ──▶ McpToolRegistry ──▶ Tool impls ──▶ Services
Browser/App ─REST─▶ FulfillmentController ─────────────────────────────▶ Services
                                                                           │
                                                            ┌──────────────┼──────────────┐
                                                            ▼              ▼              ▼
                                                     FulfillmentSvc  DatastoreSvc   PubSubSvc
                                                            │              │              │
                                                            ▼              ▼              ▼
                                                     (orchestration)  Datastore SDK  PubSubTemplate
```

## Key Source Layout

| Path | Purpose |
|------|---------|
| `controller/FulfillmentController` | REST endpoint: `POST /api/v1/fulfillment/request` |
| `mcp/McpController` | MCP endpoint: `POST /mcp` (JSON-RPC), `GET /mcp/sse` (SSE) |
| `mcp/McpToolRegistry` | Tool registration and lookup |
| `mcp/McpTool` | Interface all MCP tools implement |
| `mcp/McpServerConfig` | Registers 6 tools at startup |
| `mcp/tools/*` | Tool implementations (Create, Save, Read, Update, Delete, Publish) |
| `service/FulfillmentService` | Orchestrates: validate → save → publish → respond |
| `service/DatastoreService` | Wrapper over `RequestRepository` for MCP tools |
| `service/PubSubService` | Pub/Sub publishing with correlation ID attributes |
| `repository/RequestRepository` | Datastore CRUD using native `com.google.cloud.datastore` client |
| `entity/RequestEntry` | Lombok `@Builder` entity with Datastore field mapping |
| `model/AgentRequest` | Inbound request DTO with Jakarta validation |
| `model/AgentResponse` | Response DTO |
| `logging/MDCUtil` | Correlation ID + Request ID via SLF4J MDC |
| `logging/CorrelationIdInterceptor` | HTTP interceptor injecting `X-Correlation-ID` |
| `config/GcpConfig` | Datastore bean, ObjectMapper bean, datastoreKind bean |
| `FulfillmentFunction` | Cloud Functions Gen 1 HttpFunction adapter |

## MCP Tools (6 registered)

1. `create_fulfillment_request` — Full flow: validate, save to Datastore, publish to Pub/Sub
2. `save_to_datastore` — Direct Datastore save
3. `read_from_datastore` — Lookup by requestId
4. `update_in_datastore` — Update status/responsePayload by requestId
5. `delete_from_datastore` — Delete by requestId
6. `publish_to_pubsub` — Publish arbitrary message with attributes

## Conventions

- **Auth:** Application Default Credentials (ADC). Never hardcode credentials or set `credentials.location` in application.yml.
- **Correlation tracing:** Every request gets `X-Correlation-ID` and `X-Request-ID` in MDC and Pub/Sub message attributes.
- **Datastore access:** Uses native `com.google.cloud.datastore.Datastore` client directly — NOT Spring Data Datastore.
- **Error handling:** `GlobalExceptionHandler` with `@RestControllerAdvice`. Return `ErrorResponse` DTO.
- **Logging:** JSON structured via `LogstashEncoder` in `logback-spring.xml`. Do NOT use `ch.qos.logback.contrib` (not in classpath).
- **Tests:** JUnit 5 + Mockito. Use `@WebMvcTest` for controller tests, plain `@ExtendWith(MockitoExtension.class)` for services. Import `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`, NOT the deprecated `@MockBean` from boot.test.mock.
- **MCP protocol:** JSON-RPC 2.0. Methods: `initialize`, `tools/list`, `tools/call`. No SDK — hand-rolled in McpController.
- **Entity mapping:** Manual `Entity ↔ RequestEntry` mapping in `RequestRepository`. No ORM.

## Build & Run

```bash
# Build (skip tests for speed)
mvn clean package -DskipTests -q

# Run locally (requires ADC: gcloud auth application-default login)
java -jar target/FulfillmentService-1.0-SNAPSHOT.jar

# Run tests
mvn test

# Build Cloud Function JAR
mvn clean package -DskipTests -P cloud-function
```

## Deployment Notes

- All GCP deployments in this org go through **Terraform CI/CD pipeline** (SA: `tf-ews-vs-icie-dev-npe`).
- Human users lack `iam.serviceAccounts.actAs` and Cloud Run/Functions admin roles.
- Terraform config: `terraform/main.tf`. Deployment scripts: `agent-engine/deploy-*.sh`.
- Cloud Function adapter targets Java 17 (`cloud-function` Maven profile).

## Known Constraints

- `threetenbp` JAR is quarantined in Equifax Nexus — must be installed to local Maven repo manually if rebuilding from clean.
- Do NOT add `com.google.cloud.logging` Logback appender — the class doesn't exist in our dependency tree.
- Protobuf version must align with `libraries-bom:26.32.0` (protobuf 3.25.2). Don't downgrade.