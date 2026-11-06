package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpPaymentProfileResponse {
    private String id;
    private String status;
    private String description;
    @JsonProperty("payment_methods")
    private List<ProfilePaymentMethod> paymentMethods;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfilePaymentMethod {
        private String id;
        private String type;
        @JsonProperty("card_id")
        private String cardId;
        @JsonProperty("default_method")
        private Boolean defaultMethod;
    }
}

