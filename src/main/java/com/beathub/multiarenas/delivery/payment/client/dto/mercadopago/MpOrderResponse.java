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
public class MpOrderResponse {

    private String id;
    private String type;
    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    private String description;

    @JsonProperty("init_point")
    private String initPoint;

    @JsonProperty("sandbox_init_point")
    private String sandboxInitPoint;

    @JsonProperty("checkout_url")
    private String checkoutUrl;

    private Transactions transactions;

    private Checkout checkout;

    private Config config;

    @JsonProperty("integration_data")
    private IntegrationData integrationData;

    @JsonProperty("date_created")
    private String dateCreated;

    @JsonProperty("date_last_updated")
    private String dateLastUpdated;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Config {
        private OnlineConfig online;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IntegrationData {
        @JsonProperty("application_id")
        private String applicationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OnlineConfig {
        @JsonProperty("init_point")
        private String initPoint;
        @JsonProperty("sandbox_init_point")
        private String sandboxInitPoint;
        @JsonProperty("checkout_url")
        private String checkoutUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transactions {
        private List<PaymentResponse> payments;

        public List<PaymentResponse> getPayments() {
            return payments;
        }

        public void setPayments(List<PaymentResponse> payments) {
            this.payments = payments;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentResponse {
        private String id;
        private String status;
        @JsonProperty("status_detail")
        private String statusDetail;
        private BigDecimal amount;
        @JsonProperty("payment_method_id")
        private String paymentMethodId;
        @JsonProperty("payment_type_id")
        private String paymentTypeId;
        @JsonProperty("authorization_code")
        private String authorizationCode;
        @JsonProperty("reference_id")
        private String referenceId;
        @JsonProperty("paid_amount")
        private BigDecimal paidAmount;
        @JsonProperty("payment_method")
        private PaymentMethodInfo paymentMethod;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStatusDetail() {
            return statusDetail;
        }

        public void setStatusDetail(String statusDetail) {
            this.statusDetail = statusDetail;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getPaidAmount() {
            return paidAmount;
        }

        public void setPaidAmount(BigDecimal paidAmount) {
            this.paidAmount = paidAmount;
        }

        public String getPaymentMethodId() {
            return paymentMethodId;
        }

        public void setPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
        }

        public String getPaymentTypeId() {
            return paymentTypeId;
        }

        public void setPaymentTypeId(String paymentTypeId) {
            this.paymentTypeId = paymentTypeId;
        }

        public String getAuthorizationCode() {
            return authorizationCode;
        }

        public void setAuthorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
        }

        public String getReferenceId() {
            return referenceId;
        }

        public void setReferenceId(String referenceId) {
            this.referenceId = referenceId;
        }

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

        public PaymentMethodInfo getPaymentMethod() {
            return paymentMethod;
        }

        public void setPaymentMethod(PaymentMethodInfo paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        public String resolveFriendlyPaymentName() {
            if (paymentMethod != null) {
                return PaymentMethodInfo.resolveFriendlyName(paymentMethod.getId(), paymentMethod.getType());
            }
            return PaymentMethodInfo.resolveFriendlyName(paymentMethodId, paymentTypeId);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentMethodInfo {
        private String id;
        @JsonProperty("issuer_id")
        private String issuerId;
        private String type;
        private Integer installments;
        @JsonProperty("installment_amount")
        private BigDecimal installmentAmount;

        public static String resolveFriendlyName(String id, String type) {
            String idLower = id != null ? id.toLowerCase().trim() : "";
            String typeLower = type != null ? type.toLowerCase().trim() : "";

            String brand = "";
            if (idLower.contains("visa"))
                brand = "Visa";
            else if (idLower.contains("master"))
                brand = "Mastercard";
            else if (idLower.contains("amex"))
                brand = "American Express";
            else if (idLower.contains("diners"))
                brand = "Diners Club";
            else if (idLower.contains("codensa"))
                brand = "Codensa";
            else if (idLower.contains("pse"))
                return "PSE";
            else if (idLower.contains("efecty"))
                return "Efecty";
            else if (idLower.contains("nequi"))
                return "Nequi";
            else if (idLower.contains("daviplata"))
                return "DaviPlata";
            else if (idLower.contains("account_money"))
                return "Dinero en Mercado Pago";

            if (typeLower.contains("debit")) {
                return brand.isEmpty() ? "Tarjeta de débito" : "Tarjeta de débito (" + brand + ")";
            } else if (typeLower.contains("credit")) {
                return brand.isEmpty() ? "Tarjeta de crédito" : "Tarjeta de crédito (" + brand + ")";
            } else if (typeLower.contains("bank_transfer")) {
                return "Transferencia bancaria" + (brand.isEmpty() ? "" : " (" + brand + ")");
            } else if (typeLower.contains("ticket")) {
                return "Efectivo" + (brand.isEmpty() ? "" : " (" + brand + ")");
            }

            if (!brand.isEmpty()) {
                return "Tarjeta " + brand;
            }
            return "Tarjeta de crédito o débito";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Checkout {
        @JsonProperty("init_point")
        private String initPoint;
        @JsonProperty("checkout_url")
        private String checkoutUrl;

        public String getInitPoint() {
            return initPoint;
        }

        public void setInitPoint(String initPoint) {
            this.initPoint = initPoint;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }
    }

    public String resolveCheckoutUrl(boolean preferSandbox) {
        if (preferSandbox && sandboxInitPoint != null && !sandboxInitPoint.isBlank())
            return sandboxInitPoint;
        if (initPoint != null && !initPoint.isBlank())
            return initPoint;
        if (checkoutUrl != null && !checkoutUrl.isBlank())
            return checkoutUrl;
        if (config != null && config.getOnline() != null) {
            if (preferSandbox && config.getOnline().getSandboxInitPoint() != null) {
                return config.getOnline().getSandboxInitPoint();
            }
            if (config.getOnline().getInitPoint() != null) {
                return config.getOnline().getInitPoint();
            }
            if (config.getOnline().getCheckoutUrl() != null) {
                return config.getOnline().getCheckoutUrl();
            }
        }
        if (checkout != null) {
            if (checkout.getInitPoint() != null && !checkout.getInitPoint().isBlank()) {
                return checkout.getInitPoint();
            }
            if (checkout.getCheckoutUrl() != null && !checkout.getCheckoutUrl().isBlank()) {
                return checkout.getCheckoutUrl();
            }
        }
        return sandboxInitPoint != null ? sandboxInitPoint : initPoint;
    }

    public String resolveCheckoutUrl() {
        return resolveCheckoutUrl(false);
    }

    public PaymentResponse getFirstPayment() {
        if (transactions != null && transactions.getPayments() != null && !transactions.getPayments().isEmpty()) {
            return transactions.getPayments().get(0);
        }
        return null;
    }

    public String resolveAuthorizationCode() {
        PaymentResponse first = getFirstPayment();
        if (first != null) {
            String code = first.resolveAuthorizationCode();
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        return getId();
    }

    public String resolveTerminalCode() {
        if (integrationData != null && integrationData.getApplicationId() != null
                && !integrationData.getApplicationId().isBlank()) {
            return integrationData.getApplicationId();
        }
        return "MERCADOPAGO";
    }
}
