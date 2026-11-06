package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpCustomerResponse {
    private String id;
    private String email;
    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("last_name")
    private String lastName;
    private List<MpCardResponse> cards;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MpCardResponse {
        private String id;
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("first_six_digits")
        private String firstSixDigits;
        @JsonProperty("last_four_digits")
        private String lastFourDigits;
        @JsonProperty("expiration_month")
        private Integer expirationMonth;
        @JsonProperty("expiration_year")
        private Integer expirationYear;
        @JsonProperty("payment_method")
        private PaymentMethod paymentMethod;
        private Cardholder cardholder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentMethod {
        private String id;
        private String name;
        @JsonProperty("payment_type_id")
        private String paymentTypeId;
        private String thumbnail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cardholder {
        private String name;
        private Identification identification;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Identification {
        private String type;
        private String number;
    }
}

