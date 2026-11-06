package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpPreferenceRequest {

    @JsonProperty("external_reference")
    private String externalReference;

    private List<Item> items;

    private Payer payer;

    @JsonProperty("back_urls")
    private BackUrls backUrls;

    @JsonProperty("auto_return")
    private String autoReturn;

    @JsonProperty("notification_url")
    private String notificationUrl;

    @JsonProperty("statement_descriptor")
    private String statementDescriptor;

    private Boolean expires;

    @JsonProperty("expiration_date_from")
    private String expirationDateFrom;

    @JsonProperty("expiration_date_to")
    private String expirationDateTo;

    @JsonProperty("payment_methods")
    private PaymentMethods paymentMethods;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private String id;
        private String title;
        private String description;
        private Integer quantity;

        @JsonProperty("unit_price")
        private BigDecimal unitPrice;

        @JsonProperty("currency_id")
        private String currencyId;

        @JsonProperty("category_id")
        private String categoryId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payer {
        private String name;
        private String surname;
        private String email;
        private Phone phone;
        private Identification identification;
        private Address address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Phone {
        @JsonProperty("area_code")
        private String areaCode;
        private String number;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {
        @JsonProperty("zip_code")
        private String zipCode;
        @JsonProperty("street_name")
        private String streetName;
        @JsonProperty("street_number")
        private String streetNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BackUrls {
        private String success;
        private String failure;
        private String pending;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentMethods {
        private Integer installments;
        @JsonProperty("default_installments")
        private Integer defaultInstallments;
    }
}

