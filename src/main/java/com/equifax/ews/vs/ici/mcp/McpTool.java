package com.equifax.ews.vs.ici.mcp;

import java.util.Map;

/**
 * Interface for MCP tool implementations.
 * Each tool defines its name, description, JSON Schema, and execution logic.
 */
public interface McpTool {

    /** Tool name used in tools/call requests. */
    String getName();

    /** Human-readable description of what the tool does. */
    String getDescription();

    /** JSON Schema for the tool's input parameters. */
    Map<String, Object> getInputSchema();

    /** Execute the tool with the given arguments and return a result map. */
    Map<String, Object> execute(Map<String, Object> arguments);
}