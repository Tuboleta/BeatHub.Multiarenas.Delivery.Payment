package com.beathub.multiarenas.delivery.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoClientResponse {
    private Long id;
    private Long usuarioId;
    private String arenaId;
    private Long grupoPagoId;
    private BigDecimal valorTotal;
    private Integer estadoId;

    // Datos del cliente desde el pedido
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteEmail;
    private String clienteDocumento;

    // Ítems del pedido desde Ordering
    private List<CarritoItemClientResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CarritoItemClientResponse {
        private Long id;
        private String tcposArticuloId;
        private String tcposCategoriaId;
        private String nombreArticulo;
        private BigDecimal precioUnitario;
        private Integer cantidad;
        private BigDecimal subtotal;
        private Long shopId;
        private String shopNombre;
    }
}
