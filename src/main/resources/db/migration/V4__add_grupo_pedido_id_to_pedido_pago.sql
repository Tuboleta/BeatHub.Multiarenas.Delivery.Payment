-- Migración V4: Soporte para transacciones de pago agrupadas (multi-tienda)
ALTER TABLE payment.pedido_pago
    ADD COLUMN IF NOT EXISTS grupo_pedido_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_pedido_pago_grupo ON payment.pedido_pago(grupo_pedido_id);
