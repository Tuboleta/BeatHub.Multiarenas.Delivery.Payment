package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpPreferenceResponse {

    private String id;

    @JsonProperty("init_point")
    private String initPoint;

    @JsonProperty("sandbox_init_point")
    private String sandboxInitPoint;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("collector_id")
    private Long collectorId;

    @JsonProperty("operation_type")
    private String operationType;

    @JsonProperty("date_created")
    private String dateCreated;

    public String resolveCheckoutUrl(boolean preferSandbox) {
        if (preferSandbox && sandboxInitPoint != null && !sandboxInitPoint.isBlank()) {
            return sandboxInitPoint;
        }
        if (initPoint != null && !initPoint.isBlank()) {
            return initPoint;
        }
        return sandboxInitPoint;
    }
}

