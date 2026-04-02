package com.example.purchaserequest.model.entity;

import com.example.purchaserequest.model.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_requests", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_requester_id", columnList = "requester_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 500)
    private String purchaseReason;

    @Column
    private LocalDate desiredDeliveryDate;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(length = 500)
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false, updatable = false, length = 50)
    private String createdBy;

    @Column(nullable = false, length = 50)
    private String updatedBy;

    /**
     * 合計金額を計算
     */
    public BigDecimal getTotalAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 高額申請かどうかを判定
     */
    public boolean isHighAmount(BigDecimal threshold) {
        return getTotalAmount().compareTo(threshold) > 0;
    }

    /**
     * 申請を提出（下書き → 申請済み）
     */
    public void submit() {
        if (status != RequestStatus.DRAFT) {
            throw new IllegalStateException("下書き状態の申請のみ提出できます");
        }
        this.status = RequestStatus.SUBMITTED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 申請を承認
     */
    public void approve(Long approverId) {
        if (status != RequestStatus.SUBMITTED) {
            throw new IllegalStateException("申請済み状態の申請のみ承認できます");
        }
        this.status = RequestStatus.APPROVED;
        this.approverId = approverId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 申請を却下
     */
    public void reject(Long approverId, String rejectionReason) {
        if (status != RequestStatus.SUBMITTED) {
            throw new IllegalStateException("申請済み状態の申請のみ却下できます");
        }
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("却下理由は必須です");
        }
        this.status = RequestStatus.REJECTED;
        this.approverId = approverId;
        this.rejectionReason = rejectionReason;
        this.updatedAt = LocalDateTime.now();
    }
}
