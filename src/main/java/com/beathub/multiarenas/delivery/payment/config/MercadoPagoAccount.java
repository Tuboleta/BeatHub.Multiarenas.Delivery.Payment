package com.beathub.multiarenas.delivery.payment.config;

/**
 * Representa las cuentas de Mercado Pago disponibles en el sistema.
 * - STANDARD: Cuenta principal estándar para cobros regulares con cualquier medio de pago.
 * - DAVIVIENDA_10: Subcuenta homologada exclusiva para clientes Davivienda con el 10% de descuento.
 */
public enum MercadoPagoAccount {
    STANDARD,
    DAVIVIENDA_10;

    public static MercadoPagoAccount fromString(String val) {
        if (val == null || val.isBlank()) {
            return STANDARD;
        }
        String clean = val.trim().toUpperCase();
        if (clean.contains("DAVI") || clean.contains("10")) {
            return DAVIVIENDA_10;
        }
        return STANDARD;
    }

    public boolean isDavivienda() {
        return this == DAVIVIENDA_10;
    }
}
