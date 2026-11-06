package com.beathub.multiarenas.delivery.payment.client.dto.mercadopago;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpCardTokenResponse {
    private String id;
    @JsonProperty("first_six_digits")
    private String firstSixDigits;
    @JsonProperty("last_four_digits")
    private String lastFourDigits;
    @JsonProperty("expiration_month")
    private Integer expirationMonth;
    @JsonProperty("expiration_year")
    private Integer expirationYear;
    private String status;
    @JsonProperty("date_created")
    private String dateCreated;
    @JsonProperty("date_due")
    private String dateDue;
}

