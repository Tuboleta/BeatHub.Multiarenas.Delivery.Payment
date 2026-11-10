package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpPaymentDetailResponse {

    private String id;
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("payment_type_id")
    private String paymentTypeId;

    @JsonProperty("authorization_code")
    private String authorizationCode;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("date_approved")
    private String dateApproved;

    @JsonProperty("date_created")
    private String dateCreated;

    public String resolveAuthorizationCode() {
        if (referenceId != null && !referenceId.isBlank()) {
            return referenceId;
        }
        if (authorizationCode != null && !authorizationCode.isBlank()) {
            return authorizationCode;
        }
        if (id != null && !id.isBlank()) {
            return id;
        }
        return null;
    }

    public String resolveFriendlyPaymentName() {
        return MpOrderResponse.PaymentMethodInfo.resolveFriendlyName(paymentMethodId, paymentTypeId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        private List<MpPaymentDetailResponse> results;
    }
}

