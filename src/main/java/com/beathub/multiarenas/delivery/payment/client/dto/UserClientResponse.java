package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserClientResponse {
    private Long usuarioId;
    private String username;
    private String ssoId;
    private String ssoProvider;
    private String nombres;
    private String apellidos;
    private String email;
    @JsonAlias({"telefono", "phone", "celular", "mobile"})
    private String telefono;
    @JsonAlias({"numero_documento", "numeroDocumento", "documento", "docNumber"})
    private String numeroDocumento;
    @JsonAlias({"tipo_documento_id", "tipoDocumentoId"})
    private Integer tipoDocumentoId;
    @JsonAlias({"direccion", "address", "dir"})
    private String direccion;
    @JsonAlias({"ciudad", "city", "cityName", "ciudad_id", "ciudadId"})
    private String ciudad;
    @JsonAlias({"codigo_postal", "codigoPostal", "zip", "zipCode", "postalCode"})
    private String codigoPostal;
    @JsonAlias({"departamento", "state", "departamento_id", "departamentoId"})
    private String departamento;
    @JsonAlias({"creacion_fecha", "fechaCreacion", "date_created", "createdAt", "creacionFecha"})
    private LocalDateTime creacionFecha;

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
