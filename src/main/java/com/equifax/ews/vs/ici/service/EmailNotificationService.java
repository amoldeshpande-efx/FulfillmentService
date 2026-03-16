package com.equifax.ews.vs.ici.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications when messages are published.
 */
@Slf4j
@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:fulfillment-agent@equifax.com}")
    private String fromAddress;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a notification email after a Pub/Sub message is published.
     * Runs asynchronously so it doesn't block the main request flow.
     */
    @Async
    public void sendPublishNotification(String recipientEmail, String requestId,
                                         String correlationId, String messageId, String topic) {
        if (!emailEnabled) {
            log.debug("Email notifications disabled, skipping for requestId: {}", requestId);
            return;
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.debug("No recipient email provided, skipping notification for requestId: {}", requestId);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipientEmail);
            message.setSubject("Fulfillment Request Published - " + requestId);
            message.setText(buildEmailBody(requestId, correlationId, messageId, topic));

            mailSender.send(message);
            log.info("Notification email sent - to: {}, requestId: {}, correlationId: {}",
                    recipientEmail, requestId, correlationId);

        } catch (Exception e) {
            log.error("Failed to send notification email - to: {}, requestId: {}, error: {}",
                    recipientEmail, requestId, e.getMessage(), e);
        }
    }

    private String buildEmailBody(String requestId, String correlationId, String messageId, String topic) {
        return String.format("""
                Fulfillment Request Published Successfully
                
                Request ID:     %s
                Correlation ID: %s
                Pub/Sub Topic:  %s
                Message ID:     %s
                
                Your fulfillment request has been published for downstream processing.
                
                — Fulfillment Agent
                """, requestId, correlationId, topic, messageId);
    }
}