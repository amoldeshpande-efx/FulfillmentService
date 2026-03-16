---
name: add-rest-endpoint
description: 'Add a new REST API endpoint to the FulfillmentService. Use when: creating a new API route, adding a GET/POST/PUT/DELETE endpoint, extending the REST controller, adding a new resource path.'
argument-hint: 'HTTP method + path + what it does (e.g., "GET /api/v1/fulfillment/status/{id} - get request status")'
---

# Add a New REST Endpoint

## When to Use

- Adding a new API route to the fulfillment service
- Creating a new controller or extending `FulfillmentController`
- Exposing a new operation via REST

## Files to Create/Modify

1. **Controller method** — `src/main/java/com/equifax/ews/vs/ici/controller/FulfillmentController.java` (or new controller)
2. **Request/Response DTOs** (if needed) — `src/main/java/com/equifax/ews/vs/ici/model/`
3. **Service method** (if new logic) — appropriate service class
4. **Controller test** — `src/test/java/com/equifax/ews/vs/ici/controller/FulfillmentControllerTest.java`

## Procedure

### Step 1: Add Controller Method

Add to `FulfillmentController.java` (or create a new `@RestController` under `controller/`):

```java
@GetMapping("/status/{requestId}")
@Operation(
    summary = "Get request status",
    description = "Retrieve the current status of a fulfillment request by ID."
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Status retrieved",
        content = @Content(schema = @Schema(implementation = YourResponseDTO.class))),
    @ApiResponse(responseCode = "404", description = "Request not found",
        content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public ResponseEntity<YourResponseDTO> getStatus(@PathVariable String requestId) {
    MDCUtil.setRequestId(requestId);
    log.info("Looking up request status - requestId: {}", requestId);
    // Call service layer
    // Return ResponseEntity
}
```

### Step 2: Create DTOs (if needed)

Add to `src/main/java/com/equifax/ews/vs/ici/model/`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YourResponseDTO {
    private String requestId;
    private String status;
    // fields...
}
```

Use Lombok `@Data`, `@Builder`. Add `@NotBlank`/`@NotNull` from `jakarta.validation` on request DTOs.

### Step 3: Add Service Method

Add business logic to the appropriate service. Follow existing patterns:
- `FulfillmentService` for orchestrated flows
- `DatastoreService` for data access
- `PubSubService` for event publishing

### Step 4: Write Controller Test

Use `@WebMvcTest(FulfillmentController.class)` with `MockMvc`:

```java
@WebMvcTest(FulfillmentController.class)
class FulfillmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean  // org.springframework.test.web.servlet.MockBean — Spring Boot 3.4+
    private FulfillmentService fulfillmentService;

    @Test
    void getStatus_returnsOk() throws Exception {
        // given - mock service response
        // when/then
        mockMvc.perform(get("/api/v1/fulfillment/status/REQ-123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-123"));
    }
}
```

**Important:** Import `@MockBean` from `org.springframework.boot.test.autoconfigure.web.servlet` (Spring Boot 3.x), NOT the deprecated `org.springframework.boot.test.mock.bean`.

### Step 5: Add Swagger Annotations

Every endpoint needs:
- `@Operation(summary, description)` on the method
- `@ApiResponses` with `@ApiResponse` for each status code
- `@Tag` on the controller class (existing: `name = "Fulfillment"`)

### Step 6: Verify

```bash
mvn test
mvn clean package -DskipTests -q
java -jar target/FulfillmentService-1.0-SNAPSHOT.jar
# Check Swagger UI shows the new endpoint:
open http://localhost:8080/swagger-ui.html
```

## Conventions

- Base path: `/api/v1/fulfillment/` (all endpoints under this prefix)
- Use `@Valid @RequestBody` for POST/PUT input validation
- Set correlation ID via `MDCUtil.setCorrelationId()` at the start of each method
- Return `ResponseEntity<T>` with explicit status codes
- Error handling delegated to `GlobalExceptionHandler` — just throw exceptions
- Logging: log entry + exit with correlationId, requestId, and duration