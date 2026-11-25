package com.beathub.multiarenas.delivery.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitPaymentRequest {

    @NotNull(message = "El pedidoId es obligatorio")
    private Long pedidoId;

    private String grupoPedidoId;
    private List<Long> pedidoIds;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private Integer tipoPagoId = 1; // 1: PAGO_UNICO, 2: PAGO_GRUPAL (En Vaca)
    private Integer medioPagoId;
    private Long grupoPagoUsuarioId;

    private String returnUrl;
    private String failUrl;
    @JsonAlias({"callback_url", "callbackUrl"})
    private String callbackUrl;

    private String description;
    @JsonAlias({"statement_descriptor", "statementDescriptor"})
    private String statementDescriptor;

    // Bandera para tokenizar y guardar la tarjeta para futuras compras (1-Click) tras la aprobación
    private Boolean guardarTarjeta = false;

    // Bandera e identificación para campaña de 10% de descuento Clientes Davivienda
    @JsonProperty("es_descuento_davivienda")
    @JsonAlias({"esDescuentoDavivienda", "es_davivienda", "esDavivienda"})
    private Boolean esDescuentoDavivienda = false;

    @JsonProperty("cuenta")
    @JsonAlias({"mp_account", "mpAccount"})
    private String cuenta;

    public boolean isDaviviendaPromo() {
        if (Boolean.TRUE.equals(esDescuentoDavivienda)) return true;
        if (cuenta != null && (cuenta.toLowerCase().contains("davi") || cuenta.toLowerCase().contains("10"))) {
            return true;
        }
        if (extraParams != null) {
            Object promo = extraParams.get("es_descuento_davivienda");
            if (promo instanceof Boolean && (Boolean) promo) return true;
            if (promo != null && ("true".equalsIgnoreCase(promo.toString()) || "1".equals(promo.toString()))) return true;
        }
        return false;
    }

    // Token de enlace (bindingId) si el cliente paga con una tarjeta guardada
    private String bindingId;

    // Información del comprador / cliente real
    private PayerDto payer;

    // Información adicional para prevención de fraude y homologación Mercado Pago (Datos de industria)
    @JsonProperty("additional_info")
    @JsonAlias({"additionalInfo"})
    private AdditionalInfoDto additionalInfo;

    // Lista de ítems detallados de la compra (homologación Mercado Pago)
    private List<ItemDto> items;

    // Datos antifraude / jsonParams
    private Map<String, Object> extraParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayerDto {
        private String email;
        private String name;
        private String surname;
        @JsonProperty("first_name")
        @JsonAlias({"firstName", "nombres"})
        private String firstName;
        @JsonProperty("last_name")
        @JsonAlias({"lastName", "apellidos"})
        private String lastName;
        private String phone;
        @JsonProperty("phone_area_code")
        @JsonAlias({"phoneAreaCode", "areaCode"})
        private String phoneAreaCode;
        @JsonProperty("identification_type")
        @JsonAlias({"identificationType", "tipoDocumento"})
        private String identificationType;
        @JsonProperty("identification_number")
        @JsonAlias({"identificationNumber", "numeroDocumento"})
        private String identificationNumber;
        private AddressDto address;

        @JsonProperty("registration_date")
        @JsonAlias({"registrationDate", "fechaRegistro"})
        private String registrationDate;

        @JsonProperty("is_prime_user")
        @JsonAlias({"isPrimeUser", "primeUser", "esPrime"})
        private Boolean isPrimeUser;

        @JsonProperty("is_first_purchase_online")
        @JsonAlias({"isFirstPurchaseOnline", "firstPurchaseOnline", "primeraCompra"})
        private Boolean isFirstPurchaseOnline;

        @JsonProperty("authentication_type")
        @JsonAlias({"authenticationType", "tipoAutenticacion"})
        private String authenticationType;

        @JsonProperty("last_purchase")
        @JsonAlias({"lastPurchase", "ultimaCompra"})
        private String lastPurchase;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdditionalInfoDto {
        private AdditionalPayerDto payer;
        @JsonProperty("ip_address")
        @JsonAlias({"ipAddress", "ip"})
        private String ipAddress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdditionalPayerDto {
        @JsonProperty("first_name")
        @JsonAlias({"firstName", "nombres"})
        private String firstName;
        @JsonProperty("last_name")
        @JsonAlias({"lastName", "apellidos"})
        private String lastName;
        private AddressDto address;
        private String phone;
        @JsonProperty("phone_area_code")
        @JsonAlias({"phoneAreaCode", "areaCode"})
        private String phoneAreaCode;
        @JsonProperty("registration_date")
        @JsonAlias({"registrationDate", "fechaRegistro"})
        private String registrationDate;
        @JsonProperty("is_prime_user")
        @JsonAlias({"isPrimeUser", "primeUser", "esPrime"})
        private Boolean isPrimeUser;
        @JsonProperty("is_first_purchase_online")
        @JsonAlias({"isFirstPurchaseOnline", "firstPurchaseOnline", "primeraCompra"})
        private Boolean isFirstPurchaseOnline;
        @JsonProperty("authentication_type")
        @JsonAlias({"authenticationType", "tipoAutenticacion"})
        private String authenticationType;
        @JsonProperty("last_purchase")
        @JsonAlias({"lastPurchase", "ultimaCompra"})
        private String lastPurchase;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressDto {
        @JsonProperty("zip_code")
        @JsonAlias({"zipCode"})
        private String zipCode;
        @JsonProperty("street_name")
        @JsonAlias({"streetName", "direccion"})
        private String streetName;
        @JsonProperty("street_number")
        @JsonAlias({"streetNumber"})
        private String streetNumber;
        private String neighborhood;
        @JsonProperty("city_name")
        @JsonAlias({"cityName", "city", "ciudad"})
        private String cityName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemDto {
        @JsonProperty("external_code")
        @JsonAlias({"externalCode", "ticketId", "ticket_id", "id", "productoId"})
        private String externalCode;
        private String title;
        private String description;
        private Integer quantity;
        @JsonProperty("unit_price")
        @JsonAlias({"unitPrice", "precioUnitario"})
        private BigDecimal unitPrice;
        @JsonProperty("category_id")
        @JsonAlias({"categoryId", "categoriaId"})
        private String categoryId;
        @JsonProperty("picture_url")
        @JsonAlias({"pictureUrl"})
        private String pictureUrl;
        @JsonProperty("event_date")
        @JsonAlias({"eventDate"})
        private String eventDate;
    }
}
