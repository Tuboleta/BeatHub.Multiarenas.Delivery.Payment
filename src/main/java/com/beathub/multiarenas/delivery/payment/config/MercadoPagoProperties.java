package com.beathub.multiarenas.delivery.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
    private boolean sandbox = false;

    public boolean isSandbox() {
        if (sandbox) return true;
        return accessToken != null && accessToken.startsWith("TEST-");
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDefaultReturnUrl() { return defaultReturnUrl; }
    public void setDefaultReturnUrl(String defaultReturnUrl) { this.defaultReturnUrl = defaultReturnUrl; }

    public String getDefaultFailUrl() { return defaultFailUrl; }
    public void setDefaultFailUrl(String defaultFailUrl) { this.defaultFailUrl = defaultFailUrl; }

    public String getPublicWebhookUrl() { return publicWebhookUrl; }
    public void setPublicWebhookUrl(String publicWebhookUrl) { this.publicWebhookUrl = publicWebhookUrl; }

    public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }
}
