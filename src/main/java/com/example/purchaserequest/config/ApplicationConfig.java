package com.example.purchaserequest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "purchase.request")
@Getter
@Setter
public class ApplicationConfig {

    /** 高額申請閾値（円） */
    private BigDecimal highAmountThreshold = new BigDecimal("50000");

    /** ページサイズ */
    private int pageSize = 20;
}
