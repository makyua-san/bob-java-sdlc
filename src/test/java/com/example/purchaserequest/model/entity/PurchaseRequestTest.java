package com.example.purchaserequest.model.entity;

import com.example.purchaserequest.model.RequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class PurchaseRequestTest {

    private PurchaseRequest createDraftRequest() {
        return PurchaseRequest.builder()
            .id(1L)
            .requesterId(1L)
            .itemName("ノートPC")
            .quantity(2)
            .unitPrice(new BigDecimal("75000"))
            .purchaseReason("開発業務用")
            .status(RequestStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy("yamada")
            .updatedBy("yamada")
            .build();
    }

    private PurchaseRequest createSubmittedRequest() {
        return PurchaseRequest.builder()
            .id(1L)
            .requesterId(1L)
            .itemName("ノートPC")
            .quantity(2)
            .unitPrice(new BigDecimal("75000"))
            .purchaseReason("開発業務用")
            .status(RequestStatus.SUBMITTED)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .createdBy("yamada")
            .updatedBy("yamada")
            .build();
    }

    @Nested
    @DisplayName("合計金額計算テスト")
    class GetTotalAmountTest {

        @Test
        @DisplayName("数量×単価で合計金額が計算されること")
        void 合計金額が正しく計算されること() {
            PurchaseRequest request = createDraftRequest();
            assertThat(request.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        }
    }

    @Nested
    @DisplayName("高額申請判定テスト")
    class IsHighAmountTest {

        @Test
        @DisplayName("閾値を超える場合trueを返すこと")
        void 閾値超過で高額申請と判定されること() {
            PurchaseRequest request = createDraftRequest();
            assertThat(request.isHighAmount(new BigDecimal("50000"))).isTrue();
        }

        @Test
        @DisplayName("閾値以下の場合falseを返すこと")
        void 閾値以下で高額申請でないと判定されること() {
            PurchaseRequest request = createDraftRequest();
            assertThat(request.isHighAmount(new BigDecimal("200000"))).isFalse();
        }

        @Test
        @DisplayName("閾値と同額の場合falseを返すこと")
        void 閾値と同額で高額申請でないと判定されること() {
            PurchaseRequest request = createDraftRequest();
            assertThat(request.isHighAmount(new BigDecimal("150000"))).isFalse();
        }
    }

    @Nested
    @DisplayName("ステータス遷移テスト")
    class StatusTransitionTest {

        @Test
        @DisplayName("下書きから申請済みに遷移できること")
        void 下書きから提出できること() {
            PurchaseRequest request = createDraftRequest();
            request.submit();
            assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        }

        @Test
        @DisplayName("申請済み以外から提出しようとすると例外が発生すること")
        void 申請済みから提出しようとすると例外が発生すること() {
            PurchaseRequest request = createSubmittedRequest();
            assertThatThrownBy(request::submit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("下書き状態の申請のみ提出できます");
        }

        @Test
        @DisplayName("申請済みから承認できること")
        void 申請済みから承認できること() {
            PurchaseRequest request = createSubmittedRequest();
            request.approve(2L);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(request.getApproverId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("下書きから承認しようとすると例外が発生すること")
        void 下書きから承認しようとすると例外が発生すること() {
            PurchaseRequest request = createDraftRequest();
            assertThatThrownBy(() -> request.approve(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("申請済み状態の申請のみ承認できます");
        }

        @Test
        @DisplayName("申請済みから却下できること")
        void 申請済みから却下できること() {
            PurchaseRequest request = createSubmittedRequest();
            request.reject(2L, "予算超過のため");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
            assertThat(request.getApproverId()).isEqualTo(2L);
            assertThat(request.getRejectionReason()).isEqualTo("予算超過のため");
        }

        @Test
        @DisplayName("却下理由なしで却下しようとすると例外が発生すること")
        void 却下理由なしで却下すると例外が発生すること() {
            PurchaseRequest request = createSubmittedRequest();
            assertThatThrownBy(() -> request.reject(2L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("却下理由は必須です");
        }

        @Test
        @DisplayName("空の却下理由で却下しようとすると例外が発生すること")
        void 空の却下理由で却下すると例外が発生すること() {
            PurchaseRequest request = createSubmittedRequest();
            assertThatThrownBy(() -> request.reject(2L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("却下理由は必須です");
        }
    }
}
