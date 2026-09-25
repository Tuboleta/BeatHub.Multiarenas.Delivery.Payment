package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoVerifyCardResponse {
    private String errorCode;
    private String errorMessage;
    private String orderId;
    private String orderNumber;
    private String authCode;
    private String actionCode;
    private String actionCodeDescription;
    private String time;
    private String eci;
    private String rrn;
    private String userMessage;

    public boolean isSuccessful() {
        return "0".equals(errorCode) || "0".equals(actionCode);
    }
}
