package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoPaymentOrderResponse {
    private String errorCode;
    private String errorMessage;
    private String info;
    private String redirect;
    private String rbsOrderId;
    private String acsUrl;
    private String paReq;
    private String termUrl;

    public boolean isSuccessful() {
        return "0".equals(errorCode);
    }

    public boolean is3dsRequired() {
        return acsUrl != null && !acsUrl.isEmpty();
    }
}
