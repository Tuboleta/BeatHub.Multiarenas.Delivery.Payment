# BeatHub Multiarenas - Microservicio de Pagos (`Payment`)

Microservicio encargado de la orquestación de pagos, pasarelas transaccionales (Credibanco REST API v27), división de pagos (Split / Grupos de Pago) y trazabilidad centralizada.

## Características

* **Integración Credibanco REST API**:
  * `register.do`: Registro de pedido y generación de botón/checkout seguro.
  * `getOrderStatusExtended.do`: Consulta de estado en tiempo real.
  * `refund.do`: Solicitud de reembolsos/anulaciones.
  * `verifyCard.do`: Verificación de tarjetas bancarias.
  * **Callbacks Webhook**: Recepción asíncrona de confirmación de pagos con validación HMAC SHA-256.
* **Trazabilidad Centralizada**: Registro de cada petición y respuesta en `public.log_servicios` (`servicio_integracion_id = 2`).
* **Seguridad**: Validación JWT en endpoints de negocio y acceso público controlado para Webhooks de pasarela.
* **Documentación**: Swagger UI integrado en `/swagger-ui.html`.

## Ejecución Local

```bash
# Variables de entorno
cp .env.example .env

# Ejecutar con Maven Wrapper
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8083/api/v1/payment/swagger-ui.html`
