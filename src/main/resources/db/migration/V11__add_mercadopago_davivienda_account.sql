-- =============================================================================
-- ESQUEMA PAYMENT: Soporte para Segunda Cuenta de Mercado Pago (Davivienda 10%)
-- =============================================================================

ALTER TABLE payment.pedido_pago
    ADD COLUMN IF NOT EXISTS mp_account VARCHAR(50) DEFAULT 'STANDARD';

CREATE INDEX IF NOT EXISTS idx_pedido_pago_mp_account ON payment.pedido_pago(mp_account);
