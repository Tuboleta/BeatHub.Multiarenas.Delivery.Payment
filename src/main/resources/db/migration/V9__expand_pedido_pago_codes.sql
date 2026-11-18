-- =============================================================================
-- ESQUEMA PAYMENT: Ampliar longitud de códigos de autorización y acción en pedido_pago
-- =============================================================================

ALTER TABLE payment.pedido_pago
    ALTER COLUMN auth_code TYPE VARCHAR(100),
    ALTER COLUMN action_code TYPE VARCHAR(50),
    ALTER COLUMN error_code TYPE VARCHAR(50);

