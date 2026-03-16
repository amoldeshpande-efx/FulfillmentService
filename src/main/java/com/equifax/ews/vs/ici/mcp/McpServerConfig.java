package com.equifax.ews.vs.ici.mcp;

import com.equifax.ews.vs.ici.mcp.tools.CreateFulfillmentRequestTool;
import com.equifax.ews.vs.ici.mcp.tools.DeleteFromDatastoreTool;
import com.equifax.ews.vs.ici.mcp.tools.PublishToPubSubTool;
import com.equifax.ews.vs.ici.mcp.tools.ReadFromDatastoreTool;
import com.equifax.ews.vs.ici.mcp.tools.SaveToDatastoreTool;
import com.equifax.ews.vs.ici.mcp.tools.UpdateInDatastoreTool;
import com.equifax.ews.vs.ici.service.DatastoreService;
import com.equifax.ews.vs.ici.service.FulfillmentService;
import com.equifax.ews.vs.ici.service.PubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * MCP server configuration that registers all tool implementations
 * with the tool registry at application startup.
 */
@Slf4j
@Configuration
public class McpServerConfig {

    private final McpToolRegistry toolRegistry;
    private final FulfillmentService fulfillmentService;
    private final DatastoreService datastoreService;
    private final PubSubService pubSubService;
    private final ObjectMapper objectMapper;

    public McpServerConfig(McpToolRegistry toolRegistry,
                           FulfillmentService fulfillmentService,
                           DatastoreService datastoreService,
                           PubSubService pubSubService,
                           ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.fulfillmentService = fulfillmentService;
        this.datastoreService = datastoreService;
        this.pubSubService = pubSubService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void registerTools() {
        toolRegistry.register(new CreateFulfillmentRequestTool(fulfillmentService, objectMapper));
        toolRegistry.register(new SaveToDatastoreTool(datastoreService, objectMapper));
        toolRegistry.register(new ReadFromDatastoreTool(datastoreService, objectMapper));
        toolRegistry.register(new UpdateInDatastoreTool(datastoreService, objectMapper));
        toolRegistry.register(new DeleteFromDatastoreTool(datastoreService));
        toolRegistry.register(new PublishToPubSubTool(pubSubService, objectMapper));

        log.info("MCP server initialized with {} tools", toolRegistry.size());
    }
}