package com.equifax.ews.vs.ici.mcp;

import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server controller implementing the Model Context Protocol over HTTP.
 * Supports two transports:
 * <ul>
 *   <li>Streamable HTTP: POST /mcp (direct JSON-RPC request/response)</li>
 *   <li>SSE: GET /mcp/sse + POST /mcp/message (Server-Sent Events transport)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@Tag(name = "MCP Server", description = "Model Context Protocol server for AI agent integration")
public class McpController {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "fulfillment-agent";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    public McpController(McpToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    // ==================== Streamable HTTP Transport ====================

    /**
     * Streamable HTTP transport — direct JSON-RPC request/response.
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MCP JSON-RPC endpoint",
            description = "Handles MCP protocol messages: initialize, tools/list, tools/call, ping")
    public ResponseEntity<Map<String, Object>> handleRequest(@RequestBody Map<String, Object> request) {
        String method = (String) request.get("method");
        Object id = request.get("id");

        // Notifications (no id) don't require a response
        if (id == null) {
            log.debug("Received MCP notification: {}", method);
            return ResponseEntity.accepted().build();
        }

        Map<String, Object> response = processJsonRpc(method, id, request);
        return ResponseEntity.ok(response);
    }

    // ==================== SSE Transport ====================

    /**
     * SSE connection endpoint. Client connects here and receives an endpoint event
     * indicating where to POST JSON-RPC messages.
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "MCP SSE connection",
            description = "Server-Sent Events endpoint for MCP streaming transport")
    public SseEmitter sseConnect() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        sessions.put(sessionId, emitter);

        emitter.onCompletion(() -> {
            sessions.remove(sessionId);
            log.debug("MCP SSE session completed: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            sessions.remove(sessionId);
            log.debug("MCP SSE session timed out: {}", sessionId);
        });
        emitter.onError(ex -> {
            sessions.remove(sessionId);
            log.warn("MCP SSE session error: {}", sessionId, ex);
        });

        // Tell the client where to POST messages
        emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/mcp/message?sessionId=" + sessionId));

        log.info("MCP SSE session established: {}", sessionId);
        return emitter;
    }

    /**
     * Message endpoint for SSE-connected clients.
     * Responses are sent back through the SSE stream.
     */
    @PostMapping(path = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MCP SSE message endpoint",
            description = "Receives JSON-RPC messages from SSE-connected clients")
    public ResponseEntity<Void> handleSseMessage(
            @RequestParam String sessionId,
            @RequestBody Map<String, Object> request) {

        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.notFound().build();
        }

        String method = (String) request.get("method");
        Object id = request.get("id");

        if (id == null) {
            log.debug("Received MCP SSE notification: {}", method);
            return ResponseEntity.accepted().build();
        }

        try {
            Map<String, Object> response = processJsonRpc(method, id, request);
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(response)));
            return ResponseEntity.accepted().build();
        } catch (IOException e) {
            log.error("Error sending SSE response - sessionId: {}", sessionId, e);
            sessions.remove(sessionId);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== JSON-RPC Dispatch ====================

    private Map<String, Object> processJsonRpc(String method, Object id, Map<String, Object> request) {
        log.debug("Processing MCP request - method: {}, id: {}", method, id);

        return switch (method) {
            case "initialize" -> handleInitialize(id, asMap(request.get("params")));
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, asMap(request.get("params")));
            case "ping" -> jsonRpcResult(id, Map.of());
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        log.info("MCP client initializing - clientInfo: {}",
                params != null ? params.get("clientInfo") : "unknown");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));

        return jsonRpcResult(id, result);
    }

    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> tools = toolRegistry.listToolDefinitions();
        log.debug("Listing MCP tools - count: {}", tools.size());
        return jsonRpcResult(id, Map.of("tools", tools));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params) {
        if (params == null) {
            return jsonRpcError(id, -32602, "Missing params");
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = asMap(params.get("arguments"));

        if (toolName == null) {
            return jsonRpcError(id, -32602, "Missing tool name");
        }

        McpTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return jsonRpcError(id, -32602, "Unknown tool: " + toolName);
        }

        // Set up correlation context for tracing
        String correlationId = arguments != null ? (String) arguments.get("correlationId") : null;
        if (correlationId != null) {
            MDCUtil.setCorrelationId(correlationId);
        } else {
            MDCUtil.setCorrelationId(MDCUtil.generateId());
        }

        try {
            log.info("Executing MCP tool: {} - correlationId: {}", toolName, MDCUtil.getCorrelationId());
            long startTime = System.currentTimeMillis();

            Map<String, Object> result = tool.execute(arguments != null ? arguments : Map.of());

            long duration = System.currentTimeMillis() - startTime;
            log.info("MCP tool executed: {} - duration_ms: {}, correlationId: {}",
                    toolName, duration, MDCUtil.getCorrelationId());

            List<Map<String, Object>> content = List.of(Map.of(
                    "type", "text",
                    "text", objectMapper.writeValueAsString(result)
            ));
            return jsonRpcResult(id, Map.of("content", content, "isError", false));

        } catch (Exception e) {
            log.error("MCP tool execution failed: {} - error: {}, correlationId: {}",
                    toolName, e.getMessage(), MDCUtil.getCorrelationId(), e);

            List<Map<String, Object>> content = List.of(Map.of(
                    "type", "text",
                    "text", "Error executing " + toolName + ": " + e.getMessage()
            ));
            return jsonRpcResult(id, Map.of("content", content, "isError", true));

        } finally {
            MDCUtil.clear();
        }
    }

    // ==================== JSON-RPC Helpers ====================

    private Map<String, Object> jsonRpcResult(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }
}