package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredibancoGetBindingsResponse {
    private String errorCode;
    private String errorMessage;
    private List<CredibancoBindingItem> bindings;

    public boolean isSuccessful() {
        return "0".equals(errorCode) || (errorCode == null && bindings != null);
    }
}
