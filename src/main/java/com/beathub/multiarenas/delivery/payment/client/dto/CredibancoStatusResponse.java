package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoStatusResponse {
    private String orderNumber;
    private Integer orderStatus;
    private String actionCode;
    private String actionCodeDescription;
    private String errorCode;
    private String errorMessage;
    private Long amount;
    private String currency;
    private String date;
    private String authCode;
    private String terminalId;
    private String ip;
    private Map<String, Object> cardAuthInfo;
    private Map<String, Object> bindingInfo;
    private List<Map<String, Object>> merchantOrderParams;

    public boolean isApproved() {
        // orderStatus = 2 (DEPOSITED / APROBADO)
        return Integer.valueOf(2).equals(orderStatus) || "0".equals(actionCode);
    }

    public String getBindingId() {
        if (bindingInfo != null && bindingInfo.get("bindingId") != null) {
            return bindingInfo.get("bindingId").toString();
        }
        if (bindingInfo != null && bindingInfo.get("id") != null) {
            return bindingInfo.get("id").toString();
        }
        if (cardAuthInfo != null && cardAuthInfo.get("bindingId") != null) {
            return cardAuthInfo.get("bindingId").toString();
        }
        return null;
    }

    public String getMaskedPan() {
        if (bindingInfo != null && bindingInfo.get("maskedPan") != null) {
            return bindingInfo.get("maskedPan").toString();
        }
        if (bindingInfo != null && bindingInfo.get("pan") != null) {
            return bindingInfo.get("pan").toString();
        }
        if (cardAuthInfo != null && cardAuthInfo.get("pan") != null) {
            return cardAuthInfo.get("pan").toString();
        }
        if (cardAuthInfo != null && cardAuthInfo.get("maskedPan") != null) {
            return cardAuthInfo.get("maskedPan").toString();
        }
        return null;
    }

    public String getExpiration() {
        if (bindingInfo != null && bindingInfo.get("expiryDate") != null) {
            return bindingInfo.get("expiryDate").toString();
        }
        if (cardAuthInfo != null && cardAuthInfo.get("expiration") != null) {
            return cardAuthInfo.get("expiration").toString();
        }
        return null;
    }

    public String getCardholderName() {
        if (cardAuthInfo != null && cardAuthInfo.get("cardholderName") != null) {
            return cardAuthInfo.get("cardholderName").toString();
        }
        return null;
    }

    public String getPaymentSystem() {
        if (bindingInfo != null && bindingInfo.get("paymentSystem") != null) {
            return bindingInfo.get("paymentSystem").toString();
        }
        if (cardAuthInfo != null && cardAuthInfo.get("paymentSystem") != null) {
            return cardAuthInfo.get("paymentSystem").toString();
        }
        return null;
    }
}
