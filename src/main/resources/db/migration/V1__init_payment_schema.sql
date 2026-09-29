-- =============================================================================
-- ESQUEMA PAYMENT: Tablas de Pagos, Pasarela Credibanco, Grupos y Facturación
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS payment;

-- 1. Tabla Tipo Pago (Modalidad de pago del pedido: Pago Único vs Pago Grupal en Vaca)
CREATE TABLE IF NOT EXISTS payment.tipo_pago (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT
);

-- 2. Tabla Medio Pago (Instrumento financiero: Tarjetas, PSE, Billeteras, Efectivo)
CREATE TABLE IF NOT EXISTS payment.medio_pago (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT
);

-- 3. Tabla Tipo Grupo Pago (Vigencia y tipología del grupo: Temporal por Día vs Permanente)
CREATE TABLE IF NOT EXISTS payment.tipo_grupo_pago (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    vigencia_dias INT,
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT
);

-- 4. Tabla Grupo Pago (Para pagos compartidos / split payments "en vaca")
CREATE TABLE IF NOT EXISTS payment.grupo_pago (
    id BIGSERIAL PRIMARY KEY,
    codigo_unico VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    arena_id VARCHAR(50),
    pedido_id BIGINT,
    tipo_grupo_pago_id INT REFERENCES payment.tipo_grupo_pago(id),
    es_permanente BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_expiracion TIMESTAMPTZ,
    valor_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_pagado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    saldo_pendiente NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    estado_id INT NOT NULL DEFAULT 1, -- 1: Creado/Activo, 2: Completado (100% Pagado), 3: Expirado, 4: Cancelado
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT,
    actualizacion_fecha TIMESTAMPTZ,
    actualizacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_grupo_pago_codigo ON payment.grupo_pago(codigo_unico);
CREATE INDEX IF NOT EXISTS idx_grupo_pago_arena ON payment.grupo_pago(arena_id);
CREATE INDEX IF NOT EXISTS idx_grupo_pago_pedido ON payment.grupo_pago(pedido_id);
CREATE INDEX IF NOT EXISTS idx_grupo_pago_estado ON payment.grupo_pago(estado_id);

-- 5. Tabla Grupo Pago Usuario (Integrantes y cuotas del grupo; asignación opcional para aporte libre)
CREATE TABLE IF NOT EXISTS payment.grupo_pago_usuario (
    id BIGSERIAL PRIMARY KEY,
    grupo_pago_id BIGINT NOT NULL REFERENCES payment.grupo_pago(id) ON DELETE CASCADE,
    usuario_id BIGINT NOT NULL,
    monto_asignado NUMERIC(12, 2), -- OPCIONAL / NULLABLE para aporte libre
    monto_pagado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    porcentaje NUMERIC(5, 2),
    es_lider BOOLEAN NOT NULL DEFAULT FALSE,
    estado_id INT NOT NULL DEFAULT 1, -- 1: Pendiente/Activo, 2: Pagado, 3: Retirado
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT,
    actualizacion_fecha TIMESTAMPTZ,
    actualizacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_grupo_pago_usuario_gp ON payment.grupo_pago_usuario(grupo_pago_id);
CREATE INDEX IF NOT EXISTS idx_grupo_pago_usuario_usr ON payment.grupo_pago_usuario(usuario_id);

-- 6. Tabla Cliente Pago (Tokens y referencias de clientes para pagos recurrentes / COF One-Click)
CREATE TABLE IF NOT EXISTS payment.cliente_pago (
    id BIGSERIAL PRIMARY KEY,
    cliente_referencia VARCHAR(100) NOT NULL,
    arena_usuario_id BIGINT NOT NULL,
    arena_id VARCHAR(50),
    tarjeta_enmascarada VARCHAR(30),
    binding_id VARCHAR(255),
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT,
    actualizacion_fecha TIMESTAMPTZ,
    actualizacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_cliente_pago_usuario ON payment.cliente_pago(arena_usuario_id);
CREATE INDEX IF NOT EXISTS idx_cliente_pago_binding ON payment.cliente_pago(binding_id);
CREATE INDEX IF NOT EXISTS idx_cliente_pago_arena ON payment.cliente_pago(arena_id);

-- 7. Tabla Tarjeta Cliente Pago (Detalle tarjetas guardadas por cliente)
CREATE TABLE IF NOT EXISTS payment.tarjeta_cliente_pago (
    id BIGSERIAL PRIMARY KEY,
    cliente_pago_id BIGINT NOT NULL REFERENCES payment.cliente_pago(id) ON DELETE CASCADE,
    tarjeta_id VARCHAR(100),
    tarjeta_enmascarada VARCHAR(30) NOT NULL,
    franquicia VARCHAR(50),
    expiracion VARCHAR(10),
    titular VARCHAR(150),
    es_predeterminada BOOLEAN DEFAULT FALSE,
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_tarjeta_cliente ON payment.tarjeta_cliente_pago(cliente_pago_id);

-- 8. Tabla Pedido Pago (Registro central de transacciones de pago individuales o grupales)
CREATE TABLE IF NOT EXISTS payment.pedido_pago (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    arena_id VARCHAR(50),
    usuario_id BIGINT NOT NULL,
    credibanco_order_id VARCHAR(100),
    md_order VARCHAR(100),
    form_url TEXT,
    tipo_pago_id INT REFERENCES payment.tipo_pago(id),
    medio_pago_id INT REFERENCES payment.medio_pago(id),
    grupo_pago_usuario_id BIGINT REFERENCES payment.grupo_pago_usuario(id),
    pasarela_id INT DEFAULT 2, -- 2: Credibanco (Servicio Integración)
    monto NUMERIC(12, 2) NOT NULL,
    moneda VARCHAR(10) DEFAULT 'COP',
    referencia_pago VARCHAR(100) NOT NULL UNIQUE,
    fecha_pago TIMESTAMPTZ,
    action_code VARCHAR(20),
    action_code_description TEXT,
    auth_code VARCHAR(20),
    error_code VARCHAR(20),
    error_message TEXT,
    ip_cliente VARCHAR(50),
    json_params TEXT,
    estado_id INT NOT NULL DEFAULT 1, -- 1: Iniciado, 2: Aprobado (Deposited), 3: Rechazado, 4: Reversado, 5: Anulado
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT,
    actualizacion_fecha TIMESTAMPTZ,
    actualizacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_pedido_pago_pedido ON payment.pedido_pago(pedido_id);
CREATE INDEX IF NOT EXISTS idx_pedido_pago_credibanco ON payment.pedido_pago(credibanco_order_id);
CREATE INDEX IF NOT EXISTS idx_pedido_pago_referencia ON payment.pedido_pago(referencia_pago);
CREATE INDEX IF NOT EXISTS idx_pedido_pago_usuario ON payment.pedido_pago(usuario_id);
CREATE INDEX IF NOT EXISTS idx_pedido_pago_estado ON payment.pedido_pago(estado_id);

-- 9. Tabla Factura
CREATE TABLE IF NOT EXISTS payment.factura (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    pedido_pago_id BIGINT REFERENCES payment.pedido_pago(id),
    referencia_factura VARCHAR(100) NOT NULL UNIQUE,
    pasarela_id INT DEFAULT 2, -- 2: Credibanco
    fecha_emision TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_url TEXT,
    total_bruto NUMERIC(12, 2) NOT NULL,
    iva NUMERIC(12, 2) DEFAULT 0.00,
    iac NUMERIC(12, 2) DEFAULT 0.00,
    total_neto NUMERIC(12, 2) NOT NULL,
    estado_id INT NOT NULL DEFAULT 1,
    creacion_fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creacion_usuario BIGINT
);

CREATE INDEX IF NOT EXISTS idx_factura_pedido ON payment.factura(pedido_id);
CREATE INDEX IF NOT EXISTS idx_factura_referencia ON payment.factura(referencia_factura);

-- =============================================================================
-- SEED DATA INICIAL PARA PAYMENT
-- =============================================================================

-- 1. Tipo Pago (Modalidad de pago del pedido / cuenta)
INSERT INTO payment.tipo_pago (id, nombre, descripcion, estado_id) VALUES
    (1, 'PAGO_UNICO', 'Pago individual donde un solo usuario cancela el 100% de la orden', 1),
    (2, 'PAGO_GRUPAL', 'Pago en vaca / compartido donde un grupo de usuarios aporta para cubrir la orden', 1)
ON CONFLICT (id) DO UPDATE SET 
    nombre = EXCLUDED.nombre, 
    descripcion = EXCLUDED.descripcion;

-- 2. Medio Pago (Instrumento de cobro financiero o físico)
INSERT INTO payment.medio_pago (id, nombre, estado_id) VALUES
    (1, 'TARJETA_CREDITO', 1),
    (2, 'TARJETA_DEBITO', 1),
    (3, 'PSE', 1),
    (4, 'BILLETERA_DIGITAL', 1),
    (5, 'EFECTIVO', 1)
ON CONFLICT (id) DO UPDATE SET
    nombre = EXCLUDED.nombre;

-- 3. Tipo Grupo Pago (Vigencia y tipología del grupo de pago / vaca)
INSERT INTO payment.tipo_grupo_pago (id, nombre, descripcion, vigencia_dias, estado_id) VALUES
    (1, 'TEMPORAL_DIA', 'Grupo temporal válido únicamente durante el día / jornada del evento', 1, 1),
    (2, 'PERMANENTE', 'Grupo permanente recurrente (palcos fijos, familias, grupos frecuentes)', NULL, 1),
    (3, 'TEMPORAL_EVENTO', 'Grupo temporal asociado específicamente a la duración de un evento determinado', NULL, 1)
ON CONFLICT (id) DO UPDATE SET 
    nombre = EXCLUDED.nombre, 
    descripcion = EXCLUDED.descripcion, 
    vigencia_dias = EXCLUDED.vigencia_dias;

SELECT setval('payment.tipo_pago_id_seq', (SELECT COALESCE(MAX(id), 1) FROM payment.tipo_pago));
SELECT setval('payment.medio_pago_id_seq', (SELECT COALESCE(MAX(id), 1) FROM payment.medio_pago));
SELECT setval('payment.tipo_grupo_pago_id_seq', (SELECT COALESCE(MAX(id), 1) FROM payment.tipo_grupo_pago));
