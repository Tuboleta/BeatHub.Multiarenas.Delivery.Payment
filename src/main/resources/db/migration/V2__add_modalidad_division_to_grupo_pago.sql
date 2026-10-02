-- V2: Agregar columnas de modalidad de división y cuota preasignada a payment.grupo_pago
ALTER TABLE payment.grupo_pago 
    ADD COLUMN IF NOT EXISTS modalidad_division VARCHAR(30) DEFAULT 'POR_PARTES_IGUALES',
    ADD COLUMN IF NOT EXISTS cantidad_personas INT DEFAULT 1,
    ADD COLUMN IF NOT EXISTS monto_preasignado NUMERIC(12, 2);
