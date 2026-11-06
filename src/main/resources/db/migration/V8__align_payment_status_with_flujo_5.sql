-- =============================================================================
-- V8: Alineación de Estados de Pago con la Tabla Global public.estado (flujo_id = 5)
-- =============================================================================
-- IDs oficiales del Flujo de Pagos (flujo_id = 5):
-- 22: INICIADO
-- 23: APROBADO
-- 24: RECHAZADO
-- 25: REVERSADO
-- 26: ANULADO

-- 1. Actualizar registros existentes con IDs secuenciales previos (1..5) hacia los oficiales
UPDATE payment.pedido_pago SET estado_id = 22 WHERE estado_id = 1;
UPDATE payment.pedido_pago SET estado_id = 23 WHERE estado_id = 2;
UPDATE payment.pedido_pago SET estado_id = 24 WHERE estado_id = 3;
UPDATE payment.pedido_pago SET estado_id = 25 WHERE estado_id = 4;
UPDATE payment.pedido_pago SET estado_id = 26 WHERE estado_id = 5;

-- 2. Actualizar el valor por defecto de la columna estado_id en payment.pedido_pago
ALTER TABLE payment.pedido_pago ALTER COLUMN estado_id SET DEFAULT 22;

