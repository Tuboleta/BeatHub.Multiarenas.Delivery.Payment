package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserClientResponse {
    private Long usuarioId;
    private String username;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;

    public String getNombreCompleto() {
        String n = nombres != null ? nombres.trim() : "";
        String a = apellidos != null ? apellidos.trim() : "";
        String completo = (n + " " + a).trim();
        if (!completo.isEmpty()) {
            return completo;
        }
        return username != null ? username : null;
    }
}
