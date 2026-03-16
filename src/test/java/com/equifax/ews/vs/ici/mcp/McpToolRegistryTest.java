package com.equifax.ews.vs.ici.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    private McpToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpToolRegistry();
    }

    @Test
    void register_andGetTool() {
        McpTool tool = createDummyTool("test_tool", "A test tool");
        registry.register(tool);

        McpTool retrieved = registry.getTool("test_tool");
        assertNotNull(retrieved);
        assertEquals("test_tool", retrieved.getName());
    }

    @Test
    void getTool_notRegistered_returnsNull() {
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    void size_reflectsRegisteredTools() {
        assertEquals(0, registry.size());

        registry.register(createDummyTool("tool1", "Tool 1"));
        assertEquals(1, registry.size());

        registry.register(createDummyTool("tool2", "Tool 2"));
        assertEquals(2, registry.size());
    }

    @Test
    void listToolDefinitions_returnsAllToolMetadata() {
        registry.register(createDummyTool("alpha", "Alpha tool"));
        registry.register(createDummyTool("beta", "Beta tool"));

        List<Map<String, Object>> definitions = registry.listToolDefinitions();

        assertEquals(2, definitions.size());
        for (Map<String, Object> def : definitions) {
            assertNotNull(def.get("name"));
            assertNotNull(def.get("description"));
            assertNotNull(def.get("inputSchema"));
        }
    }

    @Test
    void register_sameName_overwrites() {
        McpTool tool1 = createDummyTool("same_name", "First");
        McpTool tool2 = createDummyTool("same_name", "Second");

        registry.register(tool1);
        registry.register(tool2);

        assertEquals(1, registry.size());
        assertEquals("Second", registry.getTool("same_name").getDescription());
    }

    private McpTool createDummyTool(String name, String description) {
        return new McpTool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return description; }

            @Override
            public Map<String, Object> getInputSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments) {
                return Map.of("ok", true);
            }
        };
    }
}