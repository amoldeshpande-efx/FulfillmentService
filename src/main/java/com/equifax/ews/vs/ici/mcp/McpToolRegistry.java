package com.equifax.ews.vs.ici.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP tools. Manages tool registration, lookup, and listing.
 */
@Slf4j
@Component
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    public void register(McpTool tool) {
        tools.put(tool.getName(), tool);
        log.info("Registered MCP tool: {}", tool.getName());
    }

    public McpTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Returns tool definitions in MCP protocol format for tools/list response.
     */
    public List<Map<String, Object>> listToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (McpTool tool : tools.values()) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", tool.getName());
            def.put("description", tool.getDescription());
            def.put("inputSchema", tool.getInputSchema());
            definitions.add(def);
        }
        return definitions;
    }

    public int size() {
        return tools.size();
    }
}