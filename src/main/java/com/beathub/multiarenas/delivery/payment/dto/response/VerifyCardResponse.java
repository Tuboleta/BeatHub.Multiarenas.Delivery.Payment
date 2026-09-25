package com.beathub.multiarenas.delivery.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyCardResponse {
    private boolean valida;
    private String orderId;
    private String authCode;
    private String actionCode;
    private String actionCodeDescription;
    private String mensaje;
}
