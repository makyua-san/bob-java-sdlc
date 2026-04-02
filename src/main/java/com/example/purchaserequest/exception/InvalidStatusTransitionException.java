package com.example.purchaserequest.exception;

public class InvalidStatusTransitionException extends PurchaseRequestException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
