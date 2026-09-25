package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoRegisterResponse {
    private String orderId;
    private String formUrl;
    private String errorCode;
    private String errorMessage;

    public boolean isSuccessful() {
        return "0".equals(errorCode) || (errorCode == null && orderId != null);
    }
}
