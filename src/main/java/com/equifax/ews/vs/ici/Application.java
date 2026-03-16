package com.equifax.ews.vs.ici;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot application entry point for Fulfillment Agent Microservice.
 * 
 * This microservice provides:
 * - REST API endpoint for customer fulfillment requests
 * - MCP Server integration for agent-to-agent communication
 * - Google Cloud Datastore for request persistence
 * - Google Cloud Pub/Sub for event publishing
 * - Structured JSON logging with correlation ID tracing
 * - Email notifications on Pub/Sub publish
 */
@SpringBootApplication
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}