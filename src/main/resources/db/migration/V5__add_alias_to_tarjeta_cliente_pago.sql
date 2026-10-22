-- V5: Agregar campo alias a la tabla tarjeta_cliente_pago para personalizar nombres de tarjetas
ALTER TABLE payment.tarjeta_cliente_pago 
ADD COLUMN IF NOT EXISTS alias VARCHAR(100);
