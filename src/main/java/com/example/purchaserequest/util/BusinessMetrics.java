package com.example.purchaserequest.util;

import com.example.purchaserequest.model.RequestStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * 業務イベントをカウント
     */
    public void recordBusinessEvent(String action, String status) {
        Counter.builder("business_events_total")
            .tag("action", action)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    /**
     * 申請をカウント
     */
    public void recordPurchaseRequest(RequestStatus status, BigDecimal amount) {
        Counter.builder("purchase_requests_total")
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();

        Counter.builder("purchase_requests_amount_total")
            .tag("status", status.name())
            .register(meterRegistry)
            .increment(amount.doubleValue());
    }

    /**
     * 高額申請をカウント
     */
    public void recordHighAmountRequest() {
        Counter.builder("high_amount_requests_total")
            .register(meterRegistry)
            .increment();
    }
}
