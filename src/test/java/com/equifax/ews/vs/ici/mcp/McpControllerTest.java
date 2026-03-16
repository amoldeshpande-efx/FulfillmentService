package com.equifax.ews.vs.ici.mcp;

import com.equifax.ews.vs.ici.entity.RequestEntry;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.service.DatastoreService;
import com.equifax.ews.vs.ici.service.FulfillmentService;
import com.equifax.ews.vs.ici.service.PubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpControllerTest {

    @Mock
    private FulfillmentService fulfillmentService;

    @Mock
    private DatastoreService datastoreService;

    @Mock
    private PubSubService pubSubService;

    private McpController mcpController;
    private McpToolRegistry toolRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MDC.clear();
        objectMapper = new ObjectMapper();
        toolRegistry = new McpToolRegistry();
        mcpController = new McpController(toolRegistry, objectMapper);

        // Register tools via config
        McpServerConfig config = new McpServerConfig(
                toolRegistry, fulfillmentService, datastoreService, pubSubService, objectMapper);
        config.registerTools();
    }

    // ==================== Initialize ====================

    @Test
    void initialize_returnsProtocolVersionAndCapabilities() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of("clientInfo", Map.of("name", "test-agent"))
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("2.0", body.get("jsonrpc"));
        assertEquals(1, body.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals("2024-11-05", result.get("protocolVersion"));
        assertNotNull(result.get("capabilities"));
        assertNotNull(result.get("serverInfo"));

        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("fulfillment-agent", serverInfo.get("name"));
        assertEquals("1.0.0", serverInfo.get("version"));
    }

    // ==================== Tools List ====================

    @Test
    void toolsList_returnsAllSixTools() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/list"
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");

        assertEquals(6, tools.size());

        List<String> toolNames = tools.stream()
                .map(t -> (String) t.get("name"))
                .toList();

        assertTrue(toolNames.contains("create_fulfillment_request"));
        assertTrue(toolNames.contains("save_to_datastore"));
        assertTrue(toolNames.contains("read_from_datastore"));
        assertTrue(toolNames.contains("update_in_datastore"));
        assertTrue(toolNames.contains("delete_from_datastore"));
        assertTrue(toolNames.contains("publish_to_pubsub"));

        // Each tool should have name, description, inputSchema
        for (Map<String, Object> tool : tools) {
            assertNotNull(tool.get("name"));
            assertNotNull(tool.get("description"));
            assertNotNull(tool.get("inputSchema"));
        }
    }

    // ==================== Tools Call ====================

    @Test
    void toolsCall_createFulfillmentRequest_success() {
        AgentResponse mockResponse = AgentResponse.builder()
                .requestId("req-001")
                .correlationId("corr-123")
                .status("COMPLETED")
                .message("Success")
                .processingTimeMs(50L)
                .build();

        when(fulfillmentService.processFulfillmentRequest(any(AgentRequest.class)))
                .thenReturn(mockResponse);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("correlationId", "corr-123");
        arguments.put("customerId", "cust-456");
        arguments.put("orderId", "order-789");
        arguments.put("requestType", "ORDER");
        arguments.put("payload", Map.of("item", "widget"));

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of("name", "create_fulfillment_request", "arguments", arguments)
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type"));

        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("COMPLETED"));
    }

    @Test
    void toolsCall_readFromDatastore_found() {
        RequestEntry entry = RequestEntry.builder()
                .id("req-010")
                .requestId("req-010")
                .correlationId("corr-abc")
                .customerId("cust-001")
                .orderId("order-001")
                .requestType("ORDER")
                .status("COMPLETED")
                .build();

        when(datastoreService.getRequestEntry("req-010")).thenReturn(entry);

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 4,
                "method", "tools/call",
                "params", Map.of("name", "read_from_datastore",
                        "arguments", Map.of("requestId", "req-010"))
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("req-010"));
        assertTrue(text.contains("COMPLETED"));
    }

    @Test
    void toolsCall_readFromDatastore_notFound() {
        when(datastoreService.getRequestEntry("missing")).thenReturn(null);

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 5,
                "method", "tools/call",
                "params", Map.of("name", "read_from_datastore",
                        "arguments", Map.of("requestId", "missing"))
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("false")); // "found": false
    }

    @Test
    void toolsCall_deleteFromDatastore_success() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 6,
                "method", "tools/call",
                "params", Map.of("name", "delete_from_datastore",
                        "arguments", Map.of("requestId", "req-del"))
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        verify(datastoreService).deleteRequestEntry("req-del");
    }

    @Test
    void toolsCall_publishToPubsub_success() {
        when(pubSubService.publishMessage(anyString(), any(), any()))
                .thenReturn("msg-pub-001");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topic", "my-topic");
        arguments.put("message", Map.of("event", "test"));

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 7,
                "method", "tools/call",
                "params", Map.of("name", "publish_to_pubsub", "arguments", arguments)
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("msg-pub-001"));
        assertTrue(text.contains("PUBLISHED"));
    }

    @Test
    void toolsCall_saveToDatastore_success() {
        when(datastoreService.saveRequestEntry(any(AgentRequest.class)))
                .thenReturn("saved-req-001");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("correlationId", "corr-save");
        arguments.put("customerId", "cust-s1");
        arguments.put("orderId", "ord-s1");
        arguments.put("requestType", "RETURN");
        arguments.put("payload", Map.of("reason", "defective"));

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 8,
                "method", "tools/call",
                "params", Map.of("name", "save_to_datastore", "arguments", arguments)
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("saved-req-001"));
        assertTrue(text.contains("SAVED"));
    }

    @Test
    void toolsCall_updateInDatastore_success() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("requestId", "req-upd-001");
        arguments.put("status", "COMPLETED");
        arguments.put("processingTimeMs", 200);

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 9,
                "method", "tools/call",
                "params", Map.of("name", "update_in_datastore", "arguments", arguments)
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(false, result.get("isError"));

        verify(datastoreService).updateRequestEntry(eq("req-upd-001"), any(AgentResponse.class));
    }

    // ==================== Error Cases ====================

    @Test
    void toolsCall_unknownTool_returnsError() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 10,
                "method", "tools/call",
                "params", Map.of("name", "nonexistent_tool", "arguments", Map.of())
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32602, error.get("code"));
        assertTrue(((String) error.get("message")).contains("Unknown tool"));
    }

    @Test
    void toolsCall_missingToolName_returnsError() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 11,
                "method", "tools/call",
                "params", Map.of("arguments", Map.of())
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        assertNotNull(body.get("error"));
    }

    @Test
    void toolsCall_missingParams_returnsError() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 12);
        request.put("method", "tools/call");
        request.put("params", null);

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        assertNotNull(body.get("error"));
    }

    @Test
    void unknownMethod_returnsMethodNotFound() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 13,
                "method", "resources/list"
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32601, error.get("code"));
    }

    @Test
    void pingMethod_returnsEmptyResult() {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 14,
                "method", "ping"
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        assertNotNull(body.get("result"));
        assertNull(body.get("error"));
    }

    @Test
    void notification_noId_returnsAccepted() {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notifications/initialized");
        request.put("id", null);

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);

        assertEquals(202, response.getStatusCode().value());
    }

    @Test
    void toolsCall_toolExecutionFails_returnsIsErrorTrue() {
        // Use a non-blank requestId so the tool calls the service, which then throws
        when(datastoreService.getRequestEntry("fail-id"))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("requestId", "fail-id");

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", 15,
                "method", "tools/call",
                "params", Map.of("name", "read_from_datastore", "arguments", arguments)
        );

        ResponseEntity<Map<String, Object>> response = mcpController.handleRequest(request);
        Map<String, Object> body = response.getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertEquals(true, result.get("isError"));
    }
}