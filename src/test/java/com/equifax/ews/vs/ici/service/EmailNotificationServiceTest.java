package com.equifax.ews.vs.ici.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() throws Exception {
        setField("fromAddress", "fulfillment-agent@equifax.com");
        setField("emailEnabled", true);
    }

    @Test
    void sendPublishNotification_sendsEmail() {
        emailNotificationService.sendPublishNotification(
                "user@equifax.com", "req-001", "corr-001", "msg-001", "fulfillment-request-events");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("fulfillment-agent@equifax.com", sent.getFrom());
        assertArrayEquals(new String[]{"user@equifax.com"}, sent.getTo());
        assertTrue(sent.getSubject().contains("req-001"));
        assertTrue(sent.getText().contains("req-001"));
        assertTrue(sent.getText().contains("corr-001"));
        assertTrue(sent.getText().contains("msg-001"));
    }

    @Test
    void sendPublishNotification_skipsWhenDisabled() throws Exception {
        setField("emailEnabled", false);

        emailNotificationService.sendPublishNotification(
                "user@equifax.com", "req-001", "corr-001", "msg-001", "topic");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPublishNotification_skipsWhenNoEmail() {
        emailNotificationService.sendPublishNotification(
                null, "req-001", "corr-001", "msg-001", "topic");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPublishNotification_skipsWhenBlankEmail() {
        emailNotificationService.sendPublishNotification(
                "  ", "req-001", "corr-001", "msg-001", "topic");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPublishNotification_handlesMailException() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw — error is caught and logged
        assertDoesNotThrow(() -> emailNotificationService.sendPublishNotification(
                "user@equifax.com", "req-001", "corr-001", "msg-001", "topic"));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = EmailNotificationService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(emailNotificationService, value);
    }
}