package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MpPaymentProfileRequest {
    private String description;
    @JsonProperty("payment_methods")
    private List<ProfilePaymentMethod> paymentMethods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProfilePaymentMethod {
        private String id;
        @Builder.Default
        private String type = "credit_card";
        @JsonProperty("card_id")
        private Object cardId;
        @JsonProperty("default_method")
        @Builder.Default
        private Boolean defaultMethod = true;
    }
}

