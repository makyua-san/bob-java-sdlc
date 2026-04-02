package com.example.purchaserequest.model.dto;

import com.example.purchaserequest.model.RequestStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestDto {
    private Long id;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String purchaseReason;
    private LocalDate desiredDeliveryDate;
    private String remarks;
    private RequestStatus status;
    private boolean highAmount;
    private UserSummaryDto requester;
    private UserSummaryDto approver;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
