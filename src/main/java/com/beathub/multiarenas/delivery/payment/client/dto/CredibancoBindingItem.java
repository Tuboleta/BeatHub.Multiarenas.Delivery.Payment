package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoBindingItem {
    private String bindingId;
    private String id; // Alias a veces retornado por SmartVista
    private String maskedPan;
    private String pan; // Alias a veces retornado por SmartVista
    private String expiryDate;
    private String expiration; // Alias alterno
    private String paymentSystem;

    public String resolveBindingId() {
        return bindingId != null ? bindingId : id;
    }

    public String resolveMaskedPan() {
        return maskedPan != null ? maskedPan : pan;
    }

    public String resolveExpiry() {
        return expiryDate != null ? expiryDate : expiration;
    }
}
