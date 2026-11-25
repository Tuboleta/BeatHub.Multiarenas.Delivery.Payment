package com.beathub.multiarenas.delivery.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoProperties {
    private String baseUrl = "https://api.mercadopago.com";
    private String accessToken;
    private String publicKey;
    private String webhookSecret;
    private String currency = "COP";
    private String defaultReturnUrl = "http://localhost:3000/checkout/payment-return";
    private String defaultFailUrl = "http://localhost:3000/checkout/payment-return?status=failed";
    private String publicWebhookUrl;
    private String statementDescriptor = "BiteUp DAVI";
    private boolean sandbox = false;

    /**
     * Subcuenta exclusiva para la campaña de clientes Davivienda con 10% de
     * descuento
     */
    private DaviviendaAccountProperties davivienda = new DaviviendaAccountProperties();

    public boolean isSandbox() {
        if (sandbox)
            return true;
        return accessToken != null && accessToken.startsWith("TEST-");
    }

    public String resolveAccessToken(MercadoPagoAccount account) {
        if (account == MercadoPagoAccount.DAVIVIENDA_10 && davivienda != null && davivienda.isConfigured()) {
            return davivienda.getAccessToken();
        }
        return accessToken;
    }

    public String resolvePublicKey(MercadoPagoAccount account) {
        if (account == MercadoPagoAccount.DAVIVIENDA_10 && davivienda != null && davivienda.getPublicKey() != null
                && !davivienda.getPublicKey().isBlank()) {
            return davivienda.getPublicKey();
        }
        return publicKey;
    }

    public String resolveWebhookSecret(MercadoPagoAccount account) {
        if (account == MercadoPagoAccount.DAVIVIENDA_10 && davivienda != null && davivienda.getWebhookSecret() != null
                && !davivienda.getWebhookSecret().isBlank()) {
            return davivienda.getWebhookSecret();
        }
        return webhookSecret;
    }

    public String resolveStatementDescriptor(MercadoPagoAccount account) {
        if (account == MercadoPagoAccount.DAVIVIENDA_10 && davivienda != null
                && davivienda.getStatementDescriptor() != null && !davivienda.getStatementDescriptor().isBlank()) {
            return davivienda.getStatementDescriptor();
        }
        return statementDescriptor != null && !statementDescriptor.isBlank() ? statementDescriptor : "BiteUp DAVI";
    }

    public BigDecimal getDaviviendaDiscountPercentage() {
        if (davivienda != null && davivienda.getDiscountPercentage() != null) {
            return davivienda.getDiscountPercentage();
        }
        return BigDecimal.valueOf(10.00);
    }

    @Data
    public static class DaviviendaAccountProperties {
        private String accessToken;
        private String publicKey;
        private String webhookSecret;
        private String statementDescriptor = "BiteUp DAVI";
        private BigDecimal discountPercentage = BigDecimal.valueOf(10.00);
        private boolean sandbox = false;

        public boolean isConfigured() {
            return accessToken != null && !accessToken.isBlank();
        }

        public boolean isSandbox() {
            if (sandbox)
                return true;
            return accessToken != null && accessToken.startsWith("TEST-");
        }
    }
}
