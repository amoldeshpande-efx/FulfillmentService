package com.equifax.ews.vs.ici.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google Cloud Platform configuration for beans and clients.
 */
@Configuration
public class GcpConfig {

    @Value("${spring.cloud.gcp.project-id:}")
    private String projectId;

    @Value("${spring.cloud.datastore.namespace:}")
    private String datastoreNamespace;

    /**
     * ObjectMapper bean for JSON serialization/deserialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Datastore client bean configured with project ID and namespace from application.yml.
     */
    @Bean
    public Datastore datastore() {
        DatastoreOptions.Builder builder = DatastoreOptions.newBuilder();
        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }
        if (datastoreNamespace != null && !datastoreNamespace.isEmpty()) {
            builder.setNamespace(datastoreNamespace);
        }
        return builder.build().getService();
    }

    @Bean
    public String datastoreKind() {
        return "RequestEntry";
    }
}