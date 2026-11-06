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
public class MpOrderRequest {

    @Builder.Default
    private String type = "online";

    @Builder.Default
    @JsonProperty("processing_mode")
    private String processingMode = "manual";

    @Builder.Default
    @JsonProperty("capture_mode")
    private String captureMode = "automatic_async";

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("total_amount")
    private String totalAmount;

    private String description;

    private List<Item> items;

    private Payer payer;

    private Config config;

    private Transactions transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Config {
        @JsonProperty("statement_descriptor")
        private String statementDescriptor;

        private OnlineConfig online;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OnlineConfig {
        @JsonProperty("success_url")
        private String successUrl;

        @JsonProperty("failure_url")
        private String failureUrl;

        @JsonProperty("pending_url")
        private String pendingUrl;

        @JsonProperty("auto_return")
        @Builder.Default
        private String autoReturn = "approved";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        @JsonProperty("external_code")
        private String externalCode;
        private String title;
        private String description;
        @JsonProperty("category_id")
        private String categoryId;
        @JsonProperty("picture_url")
        private String pictureUrl;
        private Integer quantity;
        @JsonProperty("unit_price")
        private String unitPrice;
        @JsonProperty("event_date")
        private String eventDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payer {
        @JsonProperty("customer_id")
        private String customerId;
        private String email;
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("last_name")
        private String lastName;
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
        @JsonProperty("neighborhood")
        private String neighborhood;
        @JsonProperty("city")
        private String city;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Transactions {
        private List<Payment> payments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payment {
        private String id;
        private String amount;
        @JsonProperty("payment_method")
        private PaymentMethod paymentMethod;
        @JsonProperty("automatic_payments")
        private AutomaticPayment automaticPayments;
        @JsonProperty("stored_credential")
        private StoredCredential storedCredential;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentMethod {
        private String id;
        private String type;
        private String token;
        private Integer installments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AutomaticPayment {
        @JsonProperty("payment_profile_id")
        private String paymentProfileId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StoredCredential {
        @JsonProperty("payment_initiator")
        @Builder.Default
        private String paymentInitiator = "merchant";
        @Builder.Default
        private String reason = "recurring";
        @JsonProperty("first_payment")
        @Builder.Default
        private Boolean firstPayment = false;
    }
}
