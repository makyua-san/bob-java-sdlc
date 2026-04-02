package com.example.purchaserequest.exception;

public class UnauthorizedOperationException extends PurchaseRequestException {

    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
