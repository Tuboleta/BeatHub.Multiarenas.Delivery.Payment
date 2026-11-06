package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MpCardTokenRequest {
    @JsonProperty("card_number")
    private String cardNumber;
    private Cardholder cardholder;
    @JsonProperty("expiration_month")
    private Integer expirationMonth;
    @JsonProperty("expiration_year")
    private Integer expirationYear;
    @JsonProperty("security_code")
    private String securityCode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Cardholder {
        private String name;
        private Identification identification;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Identification {
        private String type;
        private String number;
    }
}

