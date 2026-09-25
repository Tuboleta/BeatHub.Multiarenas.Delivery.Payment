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
    private List<Map<String, Object>> merchantOrderParams;

    public boolean isApproved() {
        // orderStatus = 2 (DEPOSITED / APROBADO)
        return Integer.valueOf(2).equals(orderStatus) || "0".equals(actionCode);
    }
}
