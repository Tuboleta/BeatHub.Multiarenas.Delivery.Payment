package com.beathub.multiarenas.delivery.payment.exception;

import lombok.Getter;

@Getter
public class CredibancoApiException extends RuntimeException {
    private final String errorCode;

    public CredibancoApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CredibancoApiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
