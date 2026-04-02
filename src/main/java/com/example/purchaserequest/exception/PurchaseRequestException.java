package com.example.purchaserequest.exception;

public class PurchaseRequestException extends RuntimeException {

    public PurchaseRequestException(String message) {
        super(message);
    }

    public PurchaseRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
