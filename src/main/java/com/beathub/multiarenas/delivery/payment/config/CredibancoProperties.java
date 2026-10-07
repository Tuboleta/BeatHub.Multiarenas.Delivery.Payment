package com.beathub.multiarenas.delivery.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "credibanco")
public class CredibancoProperties {
    private String baseUrl = "https://ecouat.credibanco.com/payment/rest";
    private String username = "TEST_USER";
    private String password = "TEST_PASSWORD";
    private String secretKeyHmac = "TEST_CALLBACK_KEY";
    private String currencyCode = "170";
    private Integer sessionTimeoutSecs = 1200;
    private String defaultReturnUrl;
    private String defaultFailUrl;
    private String publicWebhookUrl;
    private Boolean mccTravelAgency = false;
    private String proxyBaseUrl = "https://ecouat.credibanco.com/proxy/rest";
}
