-- V3: Reasignar tarjetas de clientes duplicados y agregar índice único para evitar duplicados en cliente_pago

-- 1. Reasignar tarjetas de clientes duplicados hacia el cliente_pago principal (menor id por cliente_referencia)
UPDATE payment.tarjeta_cliente_pago tcp
SET cliente_pago_id = sub.min_id
FROM (
    SELECT cp.id AS duplicate_id, min_cp.min_id
    FROM payment.cliente_pago cp
    JOIN (
        SELECT cliente_referencia, MIN(id) AS min_id
        FROM payment.cliente_pago
        GROUP BY cliente_referencia
    ) min_cp ON cp.cliente_referencia = min_cp.cliente_referencia
    WHERE cp.id != min_cp.min_id
) sub
WHERE tcp.cliente_pago_id = sub.duplicate_id;

-- 2. Eliminar registros duplicados de cliente_pago que no sean el principal
DELETE FROM payment.cliente_pago cp
WHERE cp.id NOT IN (
    SELECT MIN(id)
    FROM payment.cliente_pago
    GROUP BY cliente_referencia
);

-- 3. Crear índice único para garantizar que nunca vuelva a existir más de un cliente_pago por cliente_referencia
CREATE UNIQUE INDEX IF NOT EXISTS uq_cliente_pago_referencia ON payment.cliente_pago(cliente_referencia);
