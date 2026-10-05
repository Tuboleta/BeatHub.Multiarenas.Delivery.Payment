# 💳 Documentación de Endpoints - BeatHub Delivery Payment

Microservicio transaccional responsable de la integración con la pasarela de pagos de Credibanco (redirección a checkout hosted, callbacks de webhook, verificación de tarjetas y reembolsos) y la gestión integral de Pagos Divididos / Compartidos ("Vaca / Split Payment").

---

## 📌 Información General

| Parámetro | Detalle |
| :--- | :--- |
| **Nombre Microservicio** | `beathub-delivery-payment` |
| **Puerto Local Directo** | `8083` |
| **Context Path Directo** | `/api/v1/payment` |
| **Base URL Directa** | `http://localhost:8083/api/v1/payment` |
| **Base URL Gateway** | `http://localhost:8000/api/v1/payment` |
| **Documentación Swagger**| `http://localhost:8083/api/v1/payment/swagger-ui.html` |
| **OpenAPI JSON Spec** | `http://localhost:8083/api/v1/payment/v3/api-docs` |
| **Formato de Peticiones**| `application/json; charset=UTF-8` |
| **Formato de Respuestas**| `application/json; charset=UTF-8` |

### Headers Requeridos
```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <TOKEN_JWT>   # En todas las operaciones de cliente
X-Arena-Id: <ID_ARENA>             # Delimita las llaves y terminal Credibanco de la arena
```

---

## 📋 Historial de Cambios (Changelog de Endpoints)

| Fecha | Versión | Endpoint / Recurso | Tipo de Cambio | Descripción |
| :--- | :---: | :--- | :---: | :--- |
| 2026-10-05 | 1.0.0 | Transacciones, Vacas, Webhook | Creación | Documentación técnica completa con flujo de pago único Credibanco, división de cuentas (Vaca) y Webhook. |

---

## 🧭 Índice Rápido de Endpoints

### Transacciones Credibanco
| Método | Endpoint Gateway | Requiere Auth | Resumen |
| :---: | :--- | :---: | :--- |
| `POST` | `/api/v1/payment/transacciones/iniciar` | ✅ Sí | Iniciar orden en pasarela y obtener URL de checkout seguro de Credibanco. |
| `GET` | `/api/v1/payment/transacciones/{pedidoPagoId}/estado` | ✅ Sí | Consultar y sincronizar estado de la transacción por ID. |
| `GET` | `/api/v1/payment/transacciones/referencia/{ref}/estado` | ✅ Sí | Consultar estado mediante código de referencia (`PAY-xxx`). |
| `POST` | `/api/v1/payment/transacciones/reembolso` | ✅ Sí (`ADMIN`) | Ejecutar solicitud de reversión o reembolso ante Credibanco. |
| `POST` | `/api/v1/payment/transacciones/tarjetas/verificar` | ✅ Sí | Validar autenticidad de tarjeta en Credibanco (`verifyCard.do`). |

### Grupos de Pago Compartido (La Vaca / Split Payment)
| Método | Endpoint Gateway | Requiere Auth | Resumen |
| :---: | :--- | :---: | :--- |
| `POST` | `/api/v1/payment/grupos` | ✅ Sí | Crear una nueva Vaca para un pedido (Equitativa o Libre). |
| `GET` | `/api/v1/payment/grupos/{id}` | ✅ Sí | Consultar estado y balance de una Vaca por ID. |
| `GET` | `/api/v1/payment/grupos/codigo/{codigoUnico}` | ❌ No / Opcional | Consultar Vaca por su código mnemónico (ej: `VACA-ABC123`). |
| `GET` | `/api/v1/payment/grupos/pedido/{pedidoId}` | ✅ Sí | Consultar la Vaca asociada a una orden/pedido. |
| `GET` | `/api/v1/payment/grupos/mis-grupos` | ✅ Sí | Listar todas las Vacas en las que participa el usuario autenticado. |
| `POST` | `/api/v1/payment/grupos/{id}/unirse` | ✅ Sí | Unirse a una Vaca mediante su ID. |
| `POST` | `/api/v1/payment/grupos/codigo/{codigoUnico}/unirse` | ✅ Sí | Unirse a una Vaca escaneando enlace o código único. |
| `POST` | `/api/v1/payment/grupos/{id}/participantes` | ✅ Sí (Líder) | El anfitrión/líder asigna cuota a un participante. |
| `DELETE`| `/api/v1/payment/grupos/{id}/participantes/{userId}` | ✅ Sí (Líder) | Retirar a un participante sin pagos efectuados. |
| `POST` | `/api/v1/payment/grupos/{id}/recalcular-cuotas` | ✅ Sí (Líder) | Dividir cuotas equitativamente entre los integrantes activos. |
| `POST` | `/api/v1/payment/grupos/{id}/pagar` | ✅ Sí | Iniciar el pago de la cuota o aporte del usuario en Credibanco. |
| `POST` | `/api/v1/payment/grupos/{id}/cancelar` | ✅ Sí (Líder) | Cancelar la Vaca si no tiene aportes pagados. |

### Webhook Asíncrono de Notificación
| Método | Endpoint Gateway | Requiere Auth | Resumen |
| :---: | :--- | :---: | :--- |
| `GET` / `POST` | `/api/v1/payment/webhook/credibanco/callback` | ❌ No (Público) | Callback invocado por los servidores de Credibanco al completarse un pago. |

---

## 🔍 Detalle Técnico de Endpoints

### 1. Iniciar Pago Individual (`POST /transacciones/iniciar`)
Registra la transacción en base de datos e invoca el servicio `register.do` de Credibanco para obtener la URL del formulario de pago (checkout) donde debe redirigirse el usuario.

- **Método:** `POST`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/transacciones/iniciar`
- **URL Directa:** `http://localhost:8083/api/v1/payment/transacciones/iniciar`
- **Headers:**
  - `Authorization: Bearer <TOKEN_JWT>`
  - `X-Arena-Id: 1`

#### Request Body
| Campo | Tipo | Obligatorio | Descripción | Ejemplo |
| :--- | :---: | :---: | :--- | :--- |
| `pedidoId` | `integer` | Sí | ID del pedido generado en Ordering | `105` |
| `monto` | `number` | Sí | Importe a pagar (mayor a 0.01) | `69000.00` |
| `tipoPagoId` | `integer` | No | `1`: Pago Único, `2`: Pago Grupal | `1` |
| `returnUrl` | `string` | Sí | URL de redirección en Front tras pago exitoso | `"https://app.beathub.com/pago/exitoso"` |
| `failUrl` | `string` | Sí | URL de redirección en Front si es rechazado | `"https://app.beathub.com/pago/rechazado"` |
| `description` | `string` | No | Descripción de la compra | `"Pedido #105 - Hamburguesa Clásica"` |

```json
{
  "pedidoId": 105,
  "monto": 69000.00,
  "tipoPagoId": 1,
  "returnUrl": "https://app.beathub.com/pago/exitoso",
  "failUrl": "https://app.beathub.com/pago/fallido",
  "description": "Pedido #105 - BeatHub Delivery"
}
```

#### Respuesta `200 OK`
```json
{
  "success": true,
  "message": "Orden de pago generada exitosamente",
  "data": {
    "pedidoPagoId": 45,
    "referenciaPago": "PAY-105-1728144000",
    "credibancoOrderId": "c5a89b70-1234-4567-89ab-cdef01234567",
    "formUrl": "https://ecouat.credibanco.com/payment/merchants/TEST/payment_es.html?mdOrder=c5a89b70-1234-4567-89ab-cdef01234567",
    "estado": "CREADO",
    "monto": 69000.00
  },
  "timestamp": "2026-10-05T10:20:00"
}
```

#### Flujo en Frontend
1. El frontend envía `POST /transacciones/iniciar`.
2. Al recibir `formUrl`, redirige la ventana del usuario a dicha URL (`window.location.href = data.formUrl`).
3. Credibanco procesa la tarjeta del cliente y lo retorna a `returnUrl` o `failUrl`.

#### cURL
```bash
curl -X POST "http://localhost:8000/api/v1/payment/transacciones/iniciar" \
  -H "Authorization: Bearer <TOKEN_JWT>" \
  -H "X-Arena-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "pedidoId": 105,
    "monto": 69000.00,
    "returnUrl": "http://localhost:5173/payment/success",
    "failUrl": "http://localhost:5173/payment/failure"
  }'
```

---

### 2. Consultar Estado de Transacción (`GET /transacciones/{pedidoPagoId}/estado`)
Consulta el estado de la transacción contra base de datos y sincroniza en tiempo real contra Credibanco (`getOrderStatusExtended.do`).

- **Método:** `GET`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/transacciones/{pedidoPagoId}/estado`

#### Respuesta `200 OK`
```json
{
  "success": true,
  "data": {
    "pedidoPagoId": 45,
    "referenciaPago": "PAY-105-1728144000",
    "estadoId": 22,
    "estadoNombre": "APROBADO",
    "monto": 69000.00,
    "autorizacion": "098765",
    "pan": "411111******1111",
    "fechaPago": "2026-10-05T10:22:15"
  }
}
```

#### cURL
```bash
curl -X GET "http://localhost:8000/api/v1/payment/transacciones/45/estado" \
  -H "Authorization: Bearer <TOKEN_JWT>" \
  -H "X-Arena-Id: 1"
```

---

### 3. Crear Grupo de Pago / Vaca (`POST /grupos`)
Permite al anfitrión dividir la cuenta de una orden entre varios amigos. Admite dos modalidades:
- `POR_PARTES_IGUALES`: El total se divide equitativamente entre los comensales.
- `LIBRE_PAGO`: Cada usuario decide cuánto aportar hasta completar el monto global.

- **Método:** `POST`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/grupos`

#### Request Body
```json
{
  "pedidoId": 105,
  "nombre": "Amigos Palco 10",
  "divisionTipo": "POR_PARTES_IGUALES",
  "cantidadPersonas": 3,
  "valorTotal": 69000.00,
  "observaciones": "Cuenta compartida concierto"
}
```

#### Respuesta `201 Created`
```json
{
  "success": true,
  "message": "Grupo de pago (Vaca) creado exitosamente",
  "data": {
    "id": 12,
    "codigoUnico": "VACA-A7F92B",
    "nombre": "Amigos Palco 10",
    "pedidoId": 105,
    "divisionTipo": "POR_PARTES_IGUALES",
    "valorTotal": 69000.00,
    "montoRecaudado": 0.00,
    "montoRestante": 69000.00,
    "cantidadPersonas": 3,
    "estado": "ACTIVO",
    "participantes": [
      {
        "usuarioId": 15,
        "nombres": "Carlos Gómez",
        "montoAsignado": 23000.00,
        "montoPagado": 0.00,
        "esLider": true,
        "pagado": false
      }
    ]
  }
}
```

#### cURL
```bash
curl -X POST "http://localhost:8000/api/v1/payment/grupos" \
  -H "Authorization: Bearer <TOKEN_JWT>" \
  -H "X-Arena-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "pedidoId": 105,
    "nombre": "Amigos Palco 10",
    "divisionTipo": "POR_PARTES_IGUALES",
    "cantidadPersonas": 3,
    "valorTotal": 69000.00
  }'
```

---

### 4. Consultar Vaca por Código Único (`GET /grupos/codigo/{codigoUnico}`)
Permite a un invitado consultar los detalles de la Vaca antes de unirse mediante el código alfanumérico o enlace compartido.

- **Método:** `GET`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/grupos/codigo/{codigoUnico}`
- **Autenticación:** Opcional / Pública.

#### cURL
```bash
curl -X GET "http://localhost:8000/api/v1/payment/grupos/codigo/VACA-A7F92B"
```

---

### 5. Pagar Cuota de la Vaca (`POST /grupos/{id}/pagar`)
Genera la sesión de Credibanco para que el usuario pague su cuota correspondiente en la Vaca. Cuando todos los participantes han pagado el 100% de la orden, el microservicio marca la Vaca como `COMPLETADA` y dispara automáticamente la confirmación del pedido en Ordering hacia producción en cocina TCPOS.

- **Método:** `POST`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/grupos/{id}/pagar`

#### Request Body
```json
{
  "monto": 23000.00,
  "returnUrl": "https://app.beathub.com/vaca/pago-completado",
  "failUrl": "https://app.beathub.com/vaca/pago-fallido",
  "description": "Aporte cuota Vaca Amigos Palco 10"
}
```

#### Respuesta `200 OK`
```json
{
  "success": true,
  "message": "Sesión de pago generada exitosamente",
  "data": {
    "pedidoPagoId": 48,
    "referenciaPago": "PAY-VACA-12-USR15",
    "formUrl": "https://ecouat.credibanco.com/payment/merchants/TEST/payment_es.html?mdOrder=d8b91a23-...",
    "monto": 23000.00
  }
}
```

#### cURL
```bash
curl -X POST "http://localhost:8000/api/v1/payment/grupos/12/pagar" \
  -H "Authorization: Bearer <TOKEN_JWT>" \
  -H "X-Arena-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{
    "monto": 23000.00,
    "returnUrl": "http://localhost:5173/vaca/success",
    "failUrl": "http://localhost:5173/vaca/failure"
  }'
```

---

### 6. Webhook Callback de Credibanco (`GET / POST /webhook/credibanco/callback`)
Endpoint público expuesto en Internet al cual Credibanco notifica de manera asíncrona el resultado de las transacciones (aprobada, declinada, reversada).

- **Métodos:** `GET` / `POST`
- **URL Gateway:** `http://localhost:8000/api/v1/payment/webhook/credibanco/callback`
- **URL Directa:** `http://localhost:8083/api/v1/payment/webhook/credibanco/callback`
- **Query / Form Params enviados por Credibanco:**
  - `mdOrder`
  - `orderNumber`
  - `operation`
  - `status` (`1` o `2` = Aprobado)
  - `checksum`
  - `sign_alias`

#### Respuesta esperada por Credibanco:
`200 OK` con texto plano `"OK"`.
