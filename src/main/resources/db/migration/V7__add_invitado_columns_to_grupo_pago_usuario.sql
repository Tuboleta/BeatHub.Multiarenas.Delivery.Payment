-- =============================================================================
-- ESQUEMA PAYMENT: Soporte para identificación y seguimiento de Invitados (Vaca)
-- =============================================================================

ALTER TABLE payment.grupo_pago_usuario
    ADD COLUMN IF NOT EXISTS nombre_invitado VARCHAR(120),
    ADD COLUMN IF NOT EXISTS email_invitado VARCHAR(120);

