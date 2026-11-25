-- =============================================================================
-- ESQUEMA PAYMENT: Soporte para múltiples pedidos y grupo_pedido_id en grupo_pago
-- =============================================================================

ALTER TABLE payment.grupo_pago
    ADD COLUMN IF NOT EXISTS grupo_pedido_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_grupo_pago_grupo_pedido_id ON payment.grupo_pago(grupo_pedido_id);

CREATE TABLE IF NOT EXISTS payment.grupo_pago_pedido (
    id BIGSERIAL PRIMARY KEY,
    grupo_pago_id BIGINT NOT NULL REFERENCES payment.grupo_pago(id) ON DELETE CASCADE,
    pedido_id BIGINT NOT NULL,
    creacion_fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_grupo_pago_pedido UNIQUE (grupo_pago_id, pedido_id)
);

CREATE INDEX IF NOT EXISTS idx_grupo_pago_pedido_pedido_id ON payment.grupo_pago_pedido(pedido_id);
CREATE INDEX IF NOT EXISTS idx_grupo_pago_pedido_grupo_pago_id ON payment.grupo_pago_pedido(grupo_pago_id);

-- Poblar registros iniciales a partir de grupo_pago existente
INSERT INTO payment.grupo_pago_pedido (grupo_pago_id, pedido_id, creacion_fecha)
SELECT id, pedido_id, COALESCE(creacion_fecha, CURRENT_TIMESTAMP)
FROM payment.grupo_pago
WHERE pedido_id IS NOT NULL
ON CONFLICT (grupo_pago_id, pedido_id) DO NOTHING;
