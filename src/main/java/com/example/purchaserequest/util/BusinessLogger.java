package com.example.purchaserequest.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class BusinessLogger {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 業務イベントをログ出力
     */
    public void logBusinessEvent(String action, String message, Map<String, Object> context) {
        MDC.put("action", action);
        try {
            if (context != null && !context.isEmpty()) {
                log.info("{} - Context: {}", message, toJson(context));
            } else {
                log.info(message);
            }
        } finally {
            MDC.remove("action");
        }
    }

    /**
     * エラーをログ出力
     */
    public void logError(String action, String message, Throwable throwable, Map<String, Object> context) {
        MDC.put("action", action);
        try {
            Map<String, Object> errorContext = new HashMap<>();
            if (context != null) {
                errorContext.putAll(context);
            }
            errorContext.put("errorClass", throwable.getClass().getName());
            errorContext.put("errorMessage", throwable.getMessage());

            log.error("{} - Context: {}", message, toJson(errorContext), throwable);
        } finally {
            MDC.remove("action");
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
