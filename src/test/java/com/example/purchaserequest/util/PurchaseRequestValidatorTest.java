package com.example.purchaserequest.util;

import com.example.purchaserequest.exception.InvalidStatusTransitionException;
import com.example.purchaserequest.model.RequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseRequestValidatorTest {

    private final PurchaseRequestValidator validator = new PurchaseRequestValidator();

    @ParameterizedTest
    @DisplayName("有効なステータス遷移が許可されること")
    @CsvSource({
        "DRAFT, SUBMITTED",
        "SUBMITTED, APPROVED",
        "SUBMITTED, REJECTED"
    })
    void 有効なステータス遷移が許可されること(RequestStatus current, RequestStatus target) {
        assertThatNoException().isThrownBy(() -> validator.validateStatusTransition(current, target));
    }

    @ParameterizedTest
    @DisplayName("無効なステータス遷移で例外が発生すること")
    @CsvSource({
        "DRAFT, APPROVED",
        "DRAFT, REJECTED",
        "SUBMITTED, DRAFT",
        "APPROVED, DRAFT",
        "APPROVED, SUBMITTED",
        "APPROVED, REJECTED",
        "REJECTED, DRAFT",
        "REJECTED, SUBMITTED",
        "REJECTED, APPROVED"
    })
    void 無効なステータス遷移で例外が発生すること(RequestStatus current, RequestStatus target) {
        assertThatThrownBy(() -> validator.validateStatusTransition(current, target))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
