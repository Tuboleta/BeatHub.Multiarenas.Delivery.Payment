-- =============================================================================
-- ESQUEMA PAYMENT: Soporte para Pasarela Mercado Pago (ApiOrders v1/orders)
-- =============================================================================

-- 1. Registrar MERCADOPAGO en la tabla existente de servicios de integración
--    (public.servicio_integracion: 1=TCPOS, 2=CREDIBANCO, 3=ADS_SSO, 4=MERCADOPAGO)
INSERT INTO public.servicio_integracion (id, nombre, estado_id)
VALUES (4, 'MERCADOPAGO', 1)
ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre;

-- 2. Agregar columnas para Mercado Pago en payment.pedido_pago
ALTER TABLE payment.pedido_pago
    ADD COLUMN IF NOT EXISTS mp_order_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mp_payment_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS init_point TEXT;

CREATE INDEX IF NOT EXISTS idx_pedido_pago_mp_order ON payment.pedido_pago(mp_order_id);
CREATE INDEX IF NOT EXISTS idx_pedido_pago_mp_payment ON payment.pedido_pago(mp_payment_id);

-- 3. Agregar soporte para Mercado Pago Customer en payment.cliente_pago
ALTER TABLE payment.cliente_pago
    ADD COLUMN IF NOT EXISTS mp_customer_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_cliente_pago_mp_customer ON payment.cliente_pago(mp_customer_id);

-- 4. Agregar soporte para Mercado Pago Cards y Perfiles de Pago en payment.tarjeta_cliente_pago
--    pasarela_id: 2 = Credibanco, 4 = Mercado Pago (referencia a public.servicio_integracion)
ALTER TABLE payment.tarjeta_cliente_pago
    ADD COLUMN IF NOT EXISTS mp_card_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mp_payment_profile_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_method_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pasarela_id INT DEFAULT 4;

CREATE INDEX IF NOT EXISTS idx_tarjeta_cliente_mp_card ON payment.tarjeta_cliente_pago(mp_card_id);
CREATE INDEX IF NOT EXISTS idx_tarjeta_cliente_mp_profile ON payment.tarjeta_cliente_pago(mp_payment_profile_id);
CREATE INDEX IF NOT EXISTS idx_tarjeta_cliente_pasarela ON payment.tarjeta_cliente_pago(pasarela_id);
