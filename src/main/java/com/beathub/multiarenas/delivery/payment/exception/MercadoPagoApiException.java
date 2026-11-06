package com.beathub.multiarenas.delivery.payment.exception;

import lombok.Getter;

@Getter
public class MercadoPagoApiException extends RuntimeException {
    private final String errorCode;

    public MercadoPagoApiException(String message) {
        super(message);
        this.errorCode = "MP_API_ERROR";
    }

    public MercadoPagoApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MercadoPagoApiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

