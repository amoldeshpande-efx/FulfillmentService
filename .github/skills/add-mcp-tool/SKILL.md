---
name: add-mcp-tool
description: 'Add a new MCP tool to the FulfillmentService. Use when: creating a new tool, adding MCP capability, implementing a new tools/call handler, extending agent functionality.'
argument-hint: 'Tool name and what it should do (e.g., "search_datastore - query Datastore by customerId")'
---

# Add a New MCP Tool

## When to Use

- Adding a new capability to the MCP server
- Creating a new `tools/call` handler
- Extending what LLMs/agents can do with this service

## Files to Create/Modify

1. **New tool class** — `src/main/java/com/equifax/ews/vs/ici/mcp/tools/<ToolName>Tool.java`
2. **Register in config** — `src/main/java/com/equifax/ews/vs/ici/mcp/McpServerConfig.java`
3. **New service method** (if needed) — in the appropriate service class
4. **Unit test** — `src/test/java/com/equifax/ews/vs/ici/mcp/` or service test
5. **Update agent-card.json** — `agent-engine/agent-card.json` (add skill entry)
6. **Update agent-config.json** — `agent-engine/agent-config.json` (add tool entry)

## Procedure

### Step 1: Create the Tool Class

Create `src/main/java/com/equifax/ews/vs/ici/mcp/tools/<ToolName>Tool.java` implementing `McpTool`:

```java
package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import java.util.*;

public class MyNewTool implements McpTool {

    // Inject needed services via constructor

    @Override
    public String getName() {
        return "my_new_tool";  // snake_case, used in tools/call
    }

    @Override
    public String getDescription() {
        return "Clear description of what this tool does for the LLM.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        // Add properties with "type" and "description"
        properties.put("paramName", Map.of(
            "type", "string",
            "description", "What this parameter is for"
        ));
        schema.put("properties", properties);
        schema.put("required", List.of("paramName"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        // Validate required args
        // Call service layer
        // Return result as Map<String, Object>
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        return result;
    }
}
```

### Step 2: Register in McpServerConfig

Add to the `registerTools()` method in `McpServerConfig.java`:

```java
toolRegistry.register(new MyNewTool(serviceRef, objectMapper));
```

Constructor injection: pass any services the tool needs. Available services:
- `fulfillmentService` — orchestration (validate → save → publish)
- `datastoreService` — Datastore CRUD
- `pubSubService` — Pub/Sub publishing
- `objectMapper` — JSON serialization

### Step 3: Add Service Method (if new logic needed)

If the tool needs new business logic, add a method to the appropriate service:
- `DatastoreService` for Datastore operations
- `PubSubService` for Pub/Sub operations
- `FulfillmentService` for orchestrated flows

### Step 4: Write Unit Test

Use `@ExtendWith(MockitoExtension.class)`. Mock the service dependencies. Test:
- Happy path execution
- Missing/invalid arguments throw `IllegalArgumentException`
- `getName()`, `getDescription()`, `getInputSchema()` return correct values

### Step 5: Update Agent Descriptors

Add tool to `agent-engine/agent-config.json` in `mcpConfig.tools[]`:
```json
{ "name": "my_new_tool", "description": "What it does" }
```

Add skill to `agent-engine/agent-card.json` in `skills[]`:
```json
{
  "id": "my-new-tool",
  "name": "My New Tool",
  "description": "What it does",
  "tags": ["relevant", "tags"],
  "examples": ["example natural language request"]
}
```

### Step 6: Verify

```bash
mvn test
mvn clean package -DskipTests -q
java -jar target/FulfillmentService-1.0-SNAPSHOT.jar
# Check tools/list includes the new tool:
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | python3 -m json.tool
```

## Conventions

- Tool names: `snake_case` (e.g., `search_by_customer`)
- Tool class names: PascalCase + `Tool` suffix (e.g., `SearchByCustomerTool`)
- Always validate required arguments at the start of `execute()`
- Return `Map<String, Object>` — never throw from `execute()` for expected errors; return error info in the map
- Use `LinkedHashMap` for ordered JSON output