package com.example.purchaserequest.util;

import com.example.purchaserequest.exception.InvalidStatusTransitionException;
import com.example.purchaserequest.model.RequestStatus;
import org.springframework.stereotype.Component;

@Component
public class PurchaseRequestValidator {

    /**
     * ステータス遷移の妥当性を検証
     */
    public void validateStatusTransition(RequestStatus current, RequestStatus target) {
        if (!isValidTransition(current, target)) {
            throw new InvalidStatusTransitionException(
                String.format("ステータス遷移が不正です: %s → %s", current, target)
            );
        }
    }

    private boolean isValidTransition(RequestStatus current, RequestStatus target) {
        return switch (current) {
            case DRAFT -> target == RequestStatus.SUBMITTED;
            case SUBMITTED -> target == RequestStatus.APPROVED || target == RequestStatus.REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }
}
