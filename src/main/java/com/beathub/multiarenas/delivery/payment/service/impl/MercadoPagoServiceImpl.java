package com.beathub.multiarenas.delivery.payment.service.impl;

import com.beathub.multiarenas.delivery.payment.client.AuthServiceClient;
import com.beathub.multiarenas.delivery.payment.client.MercadoPagoRestClient;
import com.beathub.multiarenas.delivery.payment.client.NotificationServiceClient;
import com.beathub.multiarenas.delivery.payment.client.OrderingServiceClient;
import com.beathub.multiarenas.delivery.payment.client.dto.UserClientResponse;
import com.beathub.multiarenas.delivery.payment.client.dto.PedidoClientResponse;
import com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.*;
import com.beathub.multiarenas.delivery.payment.config.MercadoPagoProperties;
import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.*;
import com.beathub.multiarenas.delivery.payment.entity.*;
import com.beathub.multiarenas.delivery.payment.exception.PaymentException;
import com.beathub.multiarenas.delivery.payment.exception.ResourceNotFoundException;
import com.beathub.multiarenas.delivery.payment.repository.*;
import com.beathub.multiarenas.delivery.payment.service.HmacSignatureService;
import com.beathub.multiarenas.delivery.payment.service.MercadoPagoService;
import com.beathub.multiarenas.delivery.payment.service.IntegrationAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoServiceImpl implements MercadoPagoService {

    private final PedidoPagoRepository pedidoPagoRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final GrupoPagoUsuarioRepository grupoPagoUsuarioRepository;
    private final GrupoPagoRepository grupoPagoRepository;
    private final ClientePagoRepository clientePagoRepository;
    private final TarjetaClientePagoRepository tarjetaClientePagoRepository;
    private final MercadoPagoRestClient mercadoPagoClient;
    private final OrderingServiceClient orderingClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AuthServiceClient authServiceClient;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final HmacSignatureService hmacSignatureService;
    private final ObjectMapper objectMapper;

    private static final int PASARELA_MERCADOPAGO_ID = IntegrationAuditService.SERVICIO_MERCADOPAGO;

    @Override
    @Transactional
    public PaymentInitResponse initiateOrder(InitPaymentRequest request, String arenaId, Long usuarioId) {
        String referenciaPago = "MP-ORD-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        TipoPago tipoPago = tipoPagoRepository.findById(request.getTipoPagoId())
                .orElseGet(() -> tipoPagoRepository.findById(1).orElse(null));

        MedioPago medioPago = request.getMedioPagoId() != null
                ? medioPagoRepository.findById(request.getMedioPagoId()).orElse(null)
                : null;

        GrupoPagoUsuario grupoPagoUsuario = request.getGrupoPagoUsuarioId() != null
                ? grupoPagoUsuarioRepository.findById(request.getGrupoPagoUsuarioId()).orElse(null)
                : null;

        // Validar monto contra servicio Ordering y cargar datos del pedido
        PedidoClientResponse pedidoInfo = null;
        if (request.getPedidoId() != null) {
            try {
                pedidoInfo = orderingClient.getPedido(request.getPedidoId(), null).orElse(null);
                if (pedidoInfo != null) {
                    if (pedidoInfo.getUsuarioId() != null && !pedidoInfo.getUsuarioId().equals(usuarioId)
                            && request.getGrupoPagoUsuarioId() == null) {
                        throw new PaymentException("El pedido no pertenece al usuario autenticado");
                    }
                    if (request.getGrupoPagoUsuarioId() == null && pedidoInfo.getValorTotal() != null
                            && pedidoInfo.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
                        if (request.getMonto() == null
                                || request.getMonto().compareTo(pedidoInfo.getValorTotal()) != 0) {
                            log.info("Monto de pago Mercado Pago sincronizado con valor oficial del pedido: {} -> {}",
                                    request.getMonto(), pedidoInfo.getValorTotal());
                            request.setMonto(pedidoInfo.getValorTotal());
                        }
                    }
                }
            } catch (PaymentException pe) {
                throw pe;
            } catch (Exception ex) {
                log.warn("No se pudo validar el pedido con Ordering para Mercado Pago: {}", ex.getMessage());
            }
        }

        Map<String, Object> extraParams = request.getExtraParams() != null ? new HashMap<>(request.getExtraParams())
                : new HashMap<>();
        String jsonParamsStr = null;
        try {
            jsonParamsStr = objectMapper.writeValueAsString(extraParams);
        } catch (Exception ignored) {
        }

        // 1. Guardar PedidoPago inicial
        Long effectiveUsuarioId = (usuarioId != null && usuarioId > 0) ? usuarioId : 0L;
        PedidoPago pedidoPago = PedidoPago.builder()
                .pedidoId(request.getPedidoId())
                .grupoPedidoId(request.getGrupoPedidoId())
                .arenaId(arenaId)
                .usuarioId(effectiveUsuarioId)
                .tipoPago(tipoPago)
                .medioPago(medioPago)
                .grupoPagoUsuario(grupoPagoUsuario)
                .pasarelaId(PASARELA_MERCADOPAGO_ID)
                .monto(request.getMonto())
                .moneda(mercadoPagoProperties.getCurrency())
                .referenciaPago(referenciaPago)
                .jsonParams(jsonParamsStr)
                .estadoId(PedidoPago.ESTADO_INICIADO) // 22: Iniciado (Flujo 5)
                .creacionUsuario(effectiveUsuarioId)
                .build();

        pedidoPago = pedidoPagoRepository.save(pedidoPago);

        // 2. URLs de redirecci�n (Callbacks provistos por el Front o configuraci�n)
        String returnUrl = null;
        if (request.getCallbackUrl() != null && !request.getCallbackUrl().isBlank()) {
            returnUrl = request.getCallbackUrl().trim();
        } else if (request.getReturnUrl() != null && !request.getReturnUrl().isBlank()) {
            returnUrl = request.getReturnUrl().trim();
        } else if (mercadoPagoProperties.getDefaultReturnUrl() != null
                && !mercadoPagoProperties.getDefaultReturnUrl().isBlank()) {
            returnUrl = mercadoPagoProperties.getDefaultReturnUrl();
        } else {
            returnUrl = "http://localhost:3000/checkout/payment-return";
        }

        String failUrl = null;
        if (request.getCallbackUrl() != null && !request.getCallbackUrl().isBlank()) {
            failUrl = request.getCallbackUrl().trim();
        } else if (request.getFailUrl() != null && !request.getFailUrl().isBlank()) {
            failUrl = request.getFailUrl().trim();
        } else if (mercadoPagoProperties.getDefaultFailUrl() != null
                && !mercadoPagoProperties.getDefaultFailUrl().isBlank()) {
            failUrl = mercadoPagoProperties.getDefaultFailUrl();
        } else {
            failUrl = returnUrl;
        }

        // 3. Resolver datos del cliente real (Payer)
        String payerEmail = null;
        String payerFirstName = null;
        String payerLastName = null;
        String payerPhone = null;
        String payerPhoneAreaCode = "57";
        String payerDocType = "CC";
        String payerDocNumber = null;
        MpOrderRequest.Address payerAddress = null;

        // A. Prioridad 1: Objeto 'payer' expl�cito del request (est�ndar .NET)
        if (request.getPayer() != null) {
            var p = request.getPayer();
            if (p.getEmail() != null && !p.getEmail().isBlank())
                payerEmail = p.getEmail().trim();
            if (p.getFirstName() != null && !p.getFirstName().isBlank())
                payerFirstName = p.getFirstName().trim();
            if (p.getLastName() != null && !p.getLastName().isBlank())
                payerLastName = p.getLastName().trim();
            if (p.getName() != null && !p.getName().isBlank() && payerFirstName == null)
                payerFirstName = p.getName().trim();
            if (p.getSurname() != null && !p.getSurname().isBlank() && payerLastName == null)
                payerLastName = p.getSurname().trim();
            if (p.getPhone() != null && !p.getPhone().isBlank())
                payerPhone = p.getPhone().trim();
            if (p.getPhoneAreaCode() != null && !p.getPhoneAreaCode().isBlank())
                payerPhoneAreaCode = p.getPhoneAreaCode().trim();
            if (p.getIdentificationType() != null && !p.getIdentificationType().isBlank())
                payerDocType = p.getIdentificationType().trim();
            if (p.getIdentificationNumber() != null && !p.getIdentificationNumber().isBlank())
                payerDocNumber = p.getIdentificationNumber().trim();
            if (p.getAddress() != null) {
                payerAddress = MpOrderRequest.Address.builder()
                        .zipCode(p.getAddress().getZipCode())
                        .streetName(p.getAddress().getStreetName())
                        .streetNumber(p.getAddress().getStreetNumber())
                        .neighborhood(p.getAddress().getNeighborhood())
                        .city(p.getAddress().getCityName())
                        .build();
            }
        }

        // B. Prioridad 2: extraParams del request
        if (request.getExtraParams() != null) {
            var ep = request.getExtraParams();
            if (payerEmail == null && ep.get("email") != null)
                payerEmail = String.valueOf(ep.get("email")).trim();
            if (payerEmail == null && ep.get("emailCliente") != null)
                payerEmail = String.valueOf(ep.get("emailCliente")).trim();
            if (payerFirstName == null && ep.get("nombre") != null)
                payerFirstName = String.valueOf(ep.get("nombre")).trim();
            if (payerFirstName == null && ep.get("nombreCliente") != null)
                payerFirstName = String.valueOf(ep.get("nombreCliente")).trim();
            if (payerLastName == null && ep.get("apellido") != null)
                payerLastName = String.valueOf(ep.get("apellido")).trim();
            if (payerLastName == null && ep.get("apellidoCliente") != null)
                payerLastName = String.valueOf(ep.get("apellidoCliente")).trim();
            if (payerPhone == null && ep.get("telefono") != null)
                payerPhone = String.valueOf(ep.get("telefono")).trim();
            if (payerDocNumber == null && ep.get("documento") != null)
                payerDocNumber = String.valueOf(ep.get("documento")).trim();
            if (payerDocType == null && ep.get("tipoDocumento") != null)
                payerDocType = String.valueOf(ep.get("tipoDocumento")).trim();
        }

        // C. Prioridad 3: Datos de cliente registrados en el Pedido (Ordering)
        if (pedidoInfo != null) {
            if (payerEmail == null && pedidoInfo.getClienteEmail() != null && !pedidoInfo.getClienteEmail().isBlank()) {
                payerEmail = pedidoInfo.getClienteEmail().trim();
            }
            if (payerFirstName == null && pedidoInfo.getClienteNombre() != null
                    && !pedidoInfo.getClienteNombre().isBlank()) {
                String[] parts = pedidoInfo.getClienteNombre().trim().split("\\s+", 2);
                payerFirstName = parts[0];
                if (payerLastName == null && parts.length > 1) {
                    payerLastName = parts[1];
                }
            }
            if (payerPhone == null && pedidoInfo.getClienteTelefono() != null
                    && !pedidoInfo.getClienteTelefono().isBlank()) {
                payerPhone = pedidoInfo.getClienteTelefono().trim();
            }
            if (payerDocNumber == null && pedidoInfo.getClienteDocumento() != null
                    && !pedidoInfo.getClienteDocumento().isBlank()) {
                payerDocNumber = pedidoInfo.getClienteDocumento().trim();
            }
        }

        // D. Prioridad 4: Datos del usuario autenticado en Auth Service
        if (payerEmail == null || payerFirstName == null) {
            try {
                UserClientResponse userInfo = authServiceClient.getUsuario(usuarioId).orElse(null);
                if (userInfo != null) {
                    if (payerEmail == null && userInfo.getEmail() != null && !userInfo.getEmail().isBlank()) {
                        payerEmail = userInfo.getEmail().trim();
                    }
                    if (payerFirstName == null && userInfo.getNombres() != null && !userInfo.getNombres().isBlank()) {
                        payerFirstName = userInfo.getNombres().trim();
                    }
                    if (payerLastName == null && userInfo.getApellidos() != null
                            && !userInfo.getApellidos().isBlank()) {
                        payerLastName = userInfo.getApellidos().trim();
                    }
                    if (payerPhone == null && userInfo.getTelefono() != null && !userInfo.getTelefono().isBlank()) {
                        payerPhone = userInfo.getTelefono().trim();
                    }
                }
            } catch (Exception ex) {
                log.warn("No se pudo obtener datos del usuario desde Auth: {}", ex.getMessage());
            }
        }

        if (payerEmail == null || payerEmail.isBlank()) {
            payerEmail = "cliente_" + usuarioId + "@daviarena.com";
        }
        if (payerFirstName == null || payerFirstName.isBlank()) {
            payerFirstName = "Cliente";
        }
        if (payerLastName == null || payerLastName.isBlank()) {
            payerLastName = "Registrado";
        }

        MpOrderRequest.Payer.PayerBuilder payerBuilder = MpOrderRequest.Payer.builder()
                .email(payerEmail)
                .firstName(payerFirstName)
                .lastName(payerLastName);

        if (payerPhone != null && !payerPhone.isBlank()) {
            payerBuilder.phone(MpOrderRequest.Phone.builder()
                    .areaCode(payerPhoneAreaCode != null ? payerPhoneAreaCode : "57")
                    .number(payerPhone)
                    .build());
        }

        if (payerDocNumber != null && !payerDocNumber.isBlank()) {
            payerBuilder.identification(MpOrderRequest.Identification.builder()
                    .type(payerDocType != null ? payerDocType : "CC")
                    .number(payerDocNumber)
                    .build());
        }

        if (payerAddress != null) {
            payerBuilder.address(payerAddress);
        }

        MpOrderRequest.Payer payer = payerBuilder.build();

        // 4. Mapear �tems de la compra para homologaci�n Mercado Pago
        String currency = (mercadoPagoProperties.getCurrency() != null
                && !mercadoPagoProperties.getCurrency().isBlank())
                        ? mercadoPagoProperties.getCurrency()
                        : "COP";
        boolean noDecimals = "COP".equalsIgnoreCase(currency) || "CLP".equalsIgnoreCase(currency);

        List<MpOrderRequest.Item> mpItems = new ArrayList<>();
        boolean esPagoGrupal = request.getGrupoPagoUsuarioId() != null
                || (request.getTipoPagoId() != null && request.getTipoPagoId() == 2);

        String formattedTotalAmount = noDecimals
                ? request.getMonto().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                : request.getMonto().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();

        if (esPagoGrupal) {
            String itemDescription = (request.getDescription() != null && !request.getDescription().isBlank())
                    ? request.getDescription()
                    : "Aporte Vaca Pedido #" + request.getPedidoId();
            mpItems.add(MpOrderRequest.Item.builder()
                    .externalCode("VACA-" + (request.getGrupoPagoUsuarioId() != null ? request.getGrupoPagoUsuarioId()
                            : request.getPedidoId()))
                    .title(itemDescription)
                    .description(itemDescription)
                    .categoryId("services")
                    .quantity(1)
                    .unitPrice(formattedTotalAmount)
                    .build());
        } else if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (int i = 0; i < request.getItems().size(); i++) {
                var it = request.getItems().get(i);
                BigDecimal unitPrice = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                String unitPriceStr = noDecimals
                        ? unitPrice.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                        : unitPrice.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                String extCode = it.getExternalCode() != null && !it.getExternalCode().isBlank()
                        ? it.getExternalCode()
                        : "ITEM-" + (i + 1);
                String catId = it.getCategoryId() != null && !it.getCategoryId().isBlank()
                        ? it.getCategoryId()
                        : "food_and_drink";

                mpItems.add(MpOrderRequest.Item.builder()
                        .externalCode(extCode)
                        .title(it.getTitle() != null ? it.getTitle() : "Producto " + (i + 1))
                        .description(it.getDescription() != null ? it.getDescription() : it.getTitle())
                        .categoryId(catId)
                        .pictureUrl(it.getPictureUrl())
                        .quantity(it.getQuantity() != null && it.getQuantity() > 0 ? it.getQuantity() : 1)
                        .unitPrice(unitPriceStr)
                        .eventDate(it.getEventDate())
                        .build());
            }
        } else if (pedidoInfo != null && pedidoInfo.getItems() != null && !pedidoInfo.getItems().isEmpty()) {
            for (var it : pedidoInfo.getItems()) {
                BigDecimal unitPrice = it.getPrecioUnitario() != null ? it.getPrecioUnitario() : BigDecimal.ZERO;
                String unitPriceStr = noDecimals
                        ? unitPrice.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                        : unitPrice.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                String extCode = it.getTcposArticuloId() != null && !it.getTcposArticuloId().isBlank()
                        ? it.getTcposArticuloId()
                        : "ITEM-" + it.getId();
                String catId = it.getTcposCategoriaId() != null && !it.getTcposCategoriaId().isBlank()
                        ? it.getTcposCategoriaId()
                        : "food_and_drink";

                mpItems.add(MpOrderRequest.Item.builder()
                        .externalCode(extCode)
                        .title(it.getNombreArticulo() != null ? it.getNombreArticulo() : "Producto")
                        .description(it.getShopNombre() != null ? it.getShopNombre() : it.getNombreArticulo())
                        .categoryId(catId)
                        .quantity(it.getCantidad() != null && it.getCantidad() > 0 ? it.getCantidad() : 1)
                        .unitPrice(unitPriceStr)
                        .build());
            }
        }

        if (mpItems.isEmpty()) {
            String itemDescription = (request.getDescription() != null && !request.getDescription().isBlank())
                    ? request.getDescription()
                    : "Pedido BeatHub Delivery #" + request.getPedidoId();
            mpItems.add(MpOrderRequest.Item.builder()
                    .externalCode("PED-" + request.getPedidoId())
                    .title(itemDescription)
                    .description(itemDescription)
                    .categoryId("food_and_drink")
                    .quantity(1)
                    .unitPrice(formattedTotalAmount)
                    .build());
        } else if (!esPagoGrupal) {
            // Asegurar que la suma exacta de los items coincida exactamente con
            // formattedTotalAmount requerido por Mercado Pago Orders
            BigDecimal totalItemsSum = BigDecimal.ZERO;
            for (var it : mpItems) {
                BigDecimal p = new BigDecimal(it.getUnitPrice());
                BigDecimal q = BigDecimal.valueOf(it.getQuantity());
                totalItemsSum = totalItemsSum.add(p.multiply(q));
            }

            BigDecimal diff = request.getMonto().subtract(totalItemsSum);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                String diffStr = noDecimals
                        ? diff.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                        : diff.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                mpItems.add(MpOrderRequest.Item.builder()
                        .externalCode("FEE-01")
                        .title("Tarifa de servicio / Entrega")
                        .description("Cargo por servicio y logística delivery")
                        .categoryId("services")
                        .quantity(1)
                        .unitPrice(diffStr)
                        .build());
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                // Si la suma supera el monto a cobrar (descuento o cuota parcial),
                // consolidar en un ítem único para evitar rechazo 400 de Mercado Pago
                String itemDescription = (request.getDescription() != null && !request.getDescription().isBlank())
                        ? request.getDescription()
                        : "Consumo BeatHub Delivery #" + request.getPedidoId();
                mpItems.clear();
                mpItems.add(MpOrderRequest.Item.builder()
                        .externalCode("ORD-" + request.getPedidoId())
                        .title(itemDescription)
                        .description(itemDescription)
                        .categoryId("food_and_drink")
                        .quantity(1)
                        .unitPrice(formattedTotalAmount)
                        .build());
            }
        }

        String statementDesc = (request.getStatementDescriptor() != null && !request.getStatementDescriptor().isBlank())
                ? request.getStatementDescriptor()
                : (arenaId != null ? arenaId.toUpperCase() : "DELIVERY");

        MpOrderRequest.OnlineConfig onlineConfig = MpOrderRequest.OnlineConfig.builder()
                .successUrl(returnUrl)
                .failureUrl(failUrl)
                .pendingUrl(returnUrl)
                .autoReturn("approved")
                .build();

        MpOrderRequest.Config config = MpOrderRequest.Config.builder()
                .statementDescriptor(statementDesc)
                .online(onlineConfig)
                .build();

        MpOrderRequest orderReq = MpOrderRequest.builder()
                .type("online")
                .processingMode("manual")
                .captureMode("automatic_async")
                .externalReference(referenciaPago)
                .totalAmount(formattedTotalAmount)
                .description(request.getDescription() != null ? request.getDescription()
                        : "Pago Pedido " + request.getPedidoId() + " DAVIarena")
                .items(mpItems)
                .payer(payer)
                .config(config)
                .build();

        // 5. Invocar POST /v1/orders en Mercado Pago (API Oficial Orders con Checkout
        // Pro)
        MpOrderResponse mpResp = mercadoPagoClient.createOrder(orderReq, arenaId, usuarioId, null);

        String checkoutUrl = mpResp.getCheckoutUrl() != null && !mpResp.getCheckoutUrl().isBlank()
                ? mpResp.getCheckoutUrl()
                : mpResp.resolveCheckoutUrl(mercadoPagoProperties.isSandbox());

        pedidoPago.setMpOrderId(mpResp.getId());
        pedidoPago.setInitPoint(checkoutUrl);
        pedidoPago.setFormUrl(checkoutUrl);
        pedidoPagoRepository.save(pedidoPago);

        log.info("[MercadoPagoServiceImpl] Order #{} creada en MP con ID: {}, URL Checkout: {}",
                pedidoPago.getId(), mpResp.getId(), checkoutUrl);

        return PaymentInitResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(referenciaPago)
                .orderId(mpResp.getId()) // ID unificado de la orden en Mercado Pago
                .formUrl(checkoutUrl)
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .estadoId(pedidoPago.getEstadoId())
                .estadoDescripcion("INICIADO")
                .build();
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryOrderStatus(Long pedidoPagoId, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findById(pedidoPagoId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("PedidoPago con ID " + pedidoPagoId + " no encontrado"));

        return syncAndMapOrderStatus(pedidoPago, arenaId, usuarioId);
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryOrderByReference(String referenciaPago, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findByReferenciaPago(referenciaPago)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PedidoPago con referencia " + referenciaPago + " no encontrado"));

        return syncAndMapOrderStatus(pedidoPago, arenaId, usuarioId);
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryOrderByMpOrderId(String mpOrderId, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findByMpOrderId(mpOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PedidoPago con MP Order ID " + mpOrderId + " no encontrado"));

        return syncAndMapOrderStatus(pedidoPago, arenaId, usuarioId);
    }

    @Override
    @Transactional
    public PaymentStatusResponse queryOrderByPedidoId(Long pedidoId, String arenaId, Long usuarioId) {
        List<PedidoPago> pagos = pedidoPagoRepository.findByPedidoId(pedidoId);
        if (pagos.isEmpty()) {
            throw new ResourceNotFoundException("No se encontrÃƒÂ³ registro de pago para el pedido " + pedidoId);
        }
        PedidoPago masReciente = pagos.get(pagos.size() - 1);
        return syncAndMapOrderStatus(masReciente, arenaId, usuarioId);
    }

    private PaymentStatusResponse syncAndMapOrderStatus(PedidoPago pedidoPago, String arenaId, Long usuarioId) {
        String resolvedMedioPago = null;

        // 1. Si cuenta con mpOrderId (flujo principal de Mercado Pago Orders / Checkout
        // Pro), consultar orden directamente
        if (pedidoPago.getMpOrderId() != null && !pedidoPago.getMpOrderId().isBlank()) {
            try {
                MpOrderResponse mpOrder = mercadoPagoClient.getOrder(pedidoPago.getMpOrderId(), arenaId, usuarioId);
                if (mpOrder != null) {
                    String status = mpOrder.getStatus();
                    String statusDetail = mpOrder.getStatusDetail();
                    pedidoPago.setActionCode(status);
                    pedidoPago.setActionCodeDescription(statusDetail);

                    var firstPayment = mpOrder.getFirstPayment();
                    if (firstPayment != null) {
                        // Preservar payment ID si ya está aprobado para no sobreescribir con pagos
                        // fallidos secundarios
                        if (pedidoPago.getMpPaymentId() == null
                                || !Integer.valueOf(2).equals(pedidoPago.getEstadoId())) {
                            pedidoPago.setMpPaymentId(firstPayment.getId());
                        }
                        if (firstPayment.getAuthorizationCode() != null) {
                            pedidoPago.setAuthCode(firstPayment.getAuthorizationCode());
                        }
                        resolvedMedioPago = firstPayment.resolveFriendlyPaymentName();
                    }

                    int newEstadoId = mapMpStatusToEstadoId(status, statusDetail);
                    applyStateTransition(pedidoPago, newEstadoId, status, statusDetail, resolvedMedioPago);
                }
            } catch (Exception ex) {
                log.debug("[MercadoPagoServiceImpl] Consulta a v1/orders {} no aplicable: {}",
                        pedidoPago.getMpOrderId(), ex.getMessage());
            }
        } else if (pedidoPago.getMpPaymentId() != null && !pedidoPago.getMpPaymentId().isBlank()) {
            // 2. Si cuenta con mpPaymentId (notificaciones directas de pago o tokenización)
            try {
                MpPaymentDetailResponse paymentDetail = mercadoPagoClient.getPayment(pedidoPago.getMpPaymentId(),
                        arenaId, usuarioId);
                if (paymentDetail != null) {
                    pedidoPago.setActionCode(paymentDetail.getStatus());
                    pedidoPago.setActionCodeDescription(paymentDetail.getStatusDetail());
                    if (paymentDetail.getAuthorizationCode() != null) {
                        pedidoPago.setAuthCode(paymentDetail.getAuthorizationCode());
                    }
                    resolvedMedioPago = paymentDetail.resolveFriendlyPaymentName();

                    int newEstadoId = mapMpStatusToEstadoId(paymentDetail.getStatus(), paymentDetail.getStatusDetail());
                    applyStateTransition(pedidoPago, newEstadoId, paymentDetail.getStatus(),
                            paymentDetail.getStatusDetail(), resolvedMedioPago);
                }
            } catch (Exception ex) {
                log.warn("[MercadoPagoServiceImpl] Error consultando pago {}: {}", pedidoPago.getMpPaymentId(),
                        ex.getMessage());
            }
        } else if (pedidoPago.getReferenciaPago() != null && !pedidoPago.getReferenciaPago().isBlank()) {
            // 3. Fallback únicamente si NO cuenta con mpOrderId ni mpPaymentId
            try {
                List<MpPaymentDetailResponse> payments = mercadoPagoClient
                        .searchPaymentByExternalReference(pedidoPago.getReferenciaPago(), arenaId, usuarioId);
                if (!payments.isEmpty()) {
                    MpPaymentDetailResponse p = payments.get(payments.size() - 1);
                    if (pedidoPago.getMpPaymentId() == null || !Integer.valueOf(2).equals(pedidoPago.getEstadoId())) {
                        pedidoPago.setMpPaymentId(p.getId());
                    }
                    pedidoPago.setActionCode(p.getStatus());
                    pedidoPago.setActionCodeDescription(p.getStatusDetail());
                    if (p.getAuthorizationCode() != null) {
                        pedidoPago.setAuthCode(p.getAuthorizationCode());
                    }
                    resolvedMedioPago = p.resolveFriendlyPaymentName();

                    int newEstadoId = mapMpStatusToEstadoId(p.getStatus(), p.getStatusDetail());
                    applyStateTransition(pedidoPago, newEstadoId, p.getStatus(), p.getStatusDetail(),
                            resolvedMedioPago);
                }
            } catch (Exception ex) {
                log.warn("[MercadoPagoServiceImpl] Error buscando pago por referencia {}: {}",
                        pedidoPago.getReferenciaPago(), ex.getMessage());
            }
        }

        if (resolvedMedioPago == null || resolvedMedioPago.isBlank()) {
            resolvedMedioPago = "Tarjeta de crédito o débito";
        }

        return PaymentStatusResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(pedidoPago.getReferenciaPago())
                .orderId(pedidoPago.getMpOrderId()) // Mapeo unificado de orden para front
                .mdOrder(pedidoPago.getMpPaymentId())
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .estadoId(pedidoPago.getEstadoId())
                .estadoNombre(mapEstadoNombre(pedidoPago.getEstadoId()))
                .actionCode(pedidoPago.getActionCode())
                .actionCodeDescription(pedidoPago.getActionCodeDescription())
                .authCode(pedidoPago.getAuthCode())
                .medioPago(resolvedMedioPago)
                .errorCode(pedidoPago.getErrorCode())
                .errorMessage(pedidoPago.getErrorMessage())
                .fechaPago(pedidoPago.getFechaPago())
                .build();
    }

    @Override
    @Transactional
    public PaymentTokenResponse payWithToken(PayWithTokenRequest request, String arenaId, Long usuarioId,
            String ipCliente) {
        String referenciaPago = "MP-REC-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 1. Buscar la tarjeta tokenizada guardada
        TarjetaClientePago tarjeta = null;
        if (request.getBindingId().matches("\\d+")) {
            Long cardId = Long.valueOf(request.getBindingId());
            tarjeta = tarjetaClientePagoRepository
                    .findByIdAndClientePagoArenaIdAndClientePagoArenaUsuarioId(cardId, arenaId, usuarioId)
                    .or(() -> tarjetaClientePagoRepository.findByIdAndClientePagoArenaUsuarioId(cardId, usuarioId))
                    .orElse(null);
        }
        if (tarjeta == null) {
            tarjeta = tarjetaClientePagoRepository.findByMpCardId(request.getBindingId())
                    .or(() -> tarjetaClientePagoRepository.findByMpPaymentProfileId(request.getBindingId()))
                    .orElse(null);
        }
        if (tarjeta == null) {
            throw new PaymentException(
                    "No se encontrÃƒÂ³ la tarjeta asociada con el identificador: " + request.getBindingId());
        }

        ClientePago cliente = tarjeta.getClientePago();
        if (cliente == null || cliente.getMpCustomerId() == null) {
            throw new PaymentException("El cliente de la tarjeta no tiene un perfil registrado en Mercado Pago");
        }

        // 2. Obtener o crear perfil de pago en Mercado Pago
        String paymentProfileId = tarjeta.getMpPaymentProfileId();
        if (paymentProfileId == null || paymentProfileId.isBlank()) {
            paymentProfileId = getOrCreatePaymentProfile(cliente.getMpCustomerId(), tarjeta, arenaId, usuarioId);
            tarjeta.setMpPaymentProfileId(paymentProfileId);
            tarjetaClientePagoRepository.save(tarjeta);
        }

        TipoPago tipoPago = tipoPagoRepository.findById(request.getTipoPagoId())
                .orElseGet(() -> tipoPagoRepository.findById(1).orElse(null));

        GrupoPagoUsuario grupoPagoUsuario = request.getGrupoPagoUsuarioId() != null
                ? grupoPagoUsuarioRepository.findById(request.getGrupoPagoUsuarioId()).orElse(null)
                : null;

        // 3. Crear registro PedidoPago local
        PedidoPago pedidoPago = PedidoPago.builder()
                .pedidoId(request.getPedidoId())
                .arenaId(arenaId)
                .usuarioId(usuarioId)
                .tipoPago(tipoPago)
                .grupoPagoUsuario(grupoPagoUsuario)
                .pasarelaId(PASARELA_MERCADOPAGO_ID)
                .monto(request.getMonto())
                .moneda(mercadoPagoProperties.getCurrency())
                .referenciaPago(referenciaPago)
                .ipCliente(ipCliente)
                .estadoId(1) // 1: Iniciado
                .creacionUsuario(usuarioId)
                .build();
        pedidoPago = pedidoPagoRepository.save(pedidoPago);

        // 4. Invocar cobro recurrente con ApiOrders (v1/orders)
        String totalAmountStr = request.getMonto().toPlainString();
        String description = request.getDescription() != null ? request.getDescription()
                : "Cobro One-Click Pedido #" + request.getPedidoId();

        MpOrderRequest.Item item = MpOrderRequest.Item.builder()
                .title(description)
                .description(description)
                .quantity(1)
                .unitPrice(totalAmountStr)
                .build();

        MpOrderRequest.Payment paymentItem = MpOrderRequest.Payment.builder()
                .amount(totalAmountStr)
                .automaticPayments(MpOrderRequest.AutomaticPayment.builder()
                        .paymentProfileId(paymentProfileId)
                        .build())
                .storedCredential(MpOrderRequest.StoredCredential.builder()
                        .paymentInitiator("merchant")
                        .reason("recurring")
                        .firstPayment(false)
                        .build())
                .build();

        MpOrderRequest orderReq = MpOrderRequest.builder()
                .type("online")
                .externalReference(referenciaPago)
                .processingMode("automatic")
                .captureMode("automatic")
                .totalAmount(totalAmountStr)
                .description(description)
                .payer(MpOrderRequest.Payer.builder()
                        .customerId(cliente.getMpCustomerId())
                        .build())
                .transactions(MpOrderRequest.Transactions.builder()
                        .payments(List.of(paymentItem))
                        .build())
                .items(List.of(item))
                .build();

        String idempotencyKey = UUID.randomUUID().toString();
        MpOrderResponse mpResp = mercadoPagoClient.createOrder(orderReq, arenaId, usuarioId, idempotencyKey);

        pedidoPago.setMpOrderId(mpResp.getId());
        var firstPayment = mpResp.getFirstPayment();
        if (firstPayment != null) {
            pedidoPago.setMpPaymentId(firstPayment.getId());
            pedidoPago.setAuthCode(firstPayment.getAuthorizationCode());
        }

        String mpStatus = mpResp.getStatus();
        boolean isApproved = "processed".equalsIgnoreCase(mpStatus) || "approved".equalsIgnoreCase(mpStatus);

        if (isApproved) {
            onPaymentApproved(pedidoPago, "Cobro One-Click procesado exitosamente por Mercado Pago",
                    pedidoPago.getAuthCode(), "MERCADOPAGO");
        } else {
            pedidoPago.setEstadoId(PedidoPago.ESTADO_RECHAZADO); // 24: Rechazado (Flujo 5)
            pedidoPago.setErrorMessage(mpResp.getStatusDetail() != null ? mpResp.getStatusDetail()
                    : "Pago recurrente no aprobado: " + mpStatus);
            pedidoPagoRepository.save(pedidoPago);
        }

        return PaymentTokenResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .pedidoId(pedidoPago.getPedidoId())
                .referenciaPago(referenciaPago)
                .credibancoOrderId(mpResp.getId())
                .estadoId(pedidoPago.getEstadoId())
                .estadoNombre(mapEstadoNombre(pedidoPago.getEstadoId()))
                .monto(pedidoPago.getMonto())
                .moneda(pedidoPago.getMoneda())
                .authCode(pedidoPago.getAuthCode())
                .actionCode(mpResp.getStatus())
                .mensaje(isApproved ? "Pago realizado exitosamente con tarjeta guardada"
                        : "Pago rechazado: " + mpResp.getStatusDetail())
                .requiere3ds(false)
                .fechaPago(pedidoPago.getFechaPago())
                .build();
    }

    @Override
    @Transactional
    public RefundResponse refundOrder(RefundPaymentRequest request, String arenaId, Long usuarioId) {
        PedidoPago pedidoPago = pedidoPagoRepository.findById(request.getPedidoPagoId())
                .orElseThrow(() -> new ResourceNotFoundException("PedidoPago no encontrado"));

        boolean isApproved = Integer.valueOf(PedidoPago.ESTADO_APROBADO).equals(pedidoPago.getEstadoId())
                || Integer.valueOf(2).equals(pedidoPago.getEstadoId());
        if (!isApproved) {
            throw new PaymentException("Solo se pueden reembolsar pagos en estado Aprobado");
        }

        if (pedidoPago.getMpOrderId() == null) {
            throw new PaymentException("El pago no cuenta con un ID de orden de Mercado Pago para procesar reembolso");
        }

        BigDecimal montoReembolso = request.getMonto() != null ? request.getMonto() : pedidoPago.getMonto();
        mercadoPagoClient.refundOrder(pedidoPago.getMpOrderId(), pedidoPago.getMpPaymentId(), montoReembolso, arenaId,
                usuarioId);

        pedidoPago.setEstadoId(PedidoPago.ESTADO_REVERSADO); // 25: Reversado / Reembolsado (Flujo 5)
        pedidoPagoRepository.save(pedidoPago);

        orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), PedidoPago.ESTADO_REVERSADO,
                "Reembolso procesado en Mercado Pago", null);

        return RefundResponse.builder()
                .pedidoPagoId(pedidoPago.getId())
                .credibancoOrderId(pedidoPago.getMpOrderId())
                .montoReembolsado(montoReembolso)
                .exito(true)
                .mensaje("Reembolso procesado exitosamente en Mercado Pago")
                .build();
    }

    @Override
    @Transactional
    public TarjetaGuardadaResponse tokenizeAndSaveCard(TokenizarTarjetaRequest request, String arenaId, Long usuarioId,
            String ipCliente) {
        UserClientResponse userInfo = authServiceClient.getUsuario(usuarioId).orElse(null);
        String payerEmail = userInfo != null && userInfo.getEmail() != null ? userInfo.getEmail()
                : "cliente_" + usuarioId + "@beathub.com";
        String payerName = userInfo != null && userInfo.getNombres() != null ? userInfo.getNombres() : "Cliente";
        String payerSurname = userInfo != null && userInfo.getApellidos() != null ? userInfo.getApellidos() : "BeatHub";

        ClientePago localCustomer = resolveOrCreateLocalCustomer(arenaId, usuarioId, payerEmail, payerName,
                payerSurname);

        // 1. Crear Card Token en Mercado Pago
        int expMonth = Integer.parseInt(request.getMesVencimiento().trim());
        int expYear = Integer.parseInt(request.getAnioVencimiento().trim());
        if (expYear < 100)
            expYear += 2000;

        MpCardTokenRequest tokenReq = MpCardTokenRequest.builder()
                .cardNumber(request.getPan().replaceAll("\\s+", ""))
                .expirationMonth(expMonth)
                .expirationYear(expYear)
                .securityCode(request.getCvc())
                .cardholder(MpCardTokenRequest.Cardholder.builder()
                        .name(request.getNombreTitular())
                        .build())
                .build();

        MpCardTokenResponse tokenResp = mercadoPagoClient.createCardToken(tokenReq, arenaId, usuarioId);

        // 2. Asociar tarjeta al Customer en Mercado Pago
        MpCustomerResponse.MpCardResponse mpCard = mercadoPagoClient.saveCustomerCard(localCustomer.getMpCustomerId(),
                tokenResp.getId(), arenaId, usuarioId);

        // 3. Crear Perfil de Pago recurrente
        String paymentMethodId = mpCard.getPaymentMethod() != null ? mpCard.getPaymentMethod().getId() : "visa";
        String profileId = createPaymentProfile(localCustomer.getMpCustomerId(), mpCard.getId(), paymentMethodId,
                arenaId, usuarioId);

        // 4. Guardar tarjeta en base de datos local
        String maskedCard = "************" + mpCard.getLastFourDigits();
        TarjetaClientePago tarjetaLocal = TarjetaClientePago.builder()
                .clientePago(localCustomer)
                .tarjetaId(mpCard.getId())
                .mpCardId(mpCard.getId())
                .mpPaymentProfileId(profileId)
                .paymentMethodId(paymentMethodId)
                .pasarelaId(PASARELA_MERCADOPAGO_ID)
                .tarjetaEnmascarada(maskedCard)
                .franquicia(paymentMethodId.toUpperCase())
                .expiracion(mpCard.getExpirationMonth() + "/" + mpCard.getExpirationYear())
                .titular(request.getNombreTitular())
                .alias(request.getAlias() != null ? request.getAlias()
                        : (paymentMethodId.toUpperCase() + " " + mpCard.getLastFourDigits()))
                .esPredeterminada(true)
                .estadoId(1)
                .creacionUsuario(usuarioId)
                .build();

        tarjetaLocal = tarjetaClientePagoRepository.save(tarjetaLocal);

        return TarjetaGuardadaResponse.builder()
                .id(tarjetaLocal.getId())
                .clientePagoId(localCustomer.getId())
                .bindingId(tarjetaLocal.getMpCardId())
                .tarjetaEnmascarada(maskedCard)
                .franquicia(tarjetaLocal.getFranquicia())
                .expiracion(tarjetaLocal.getExpiracion())
                .titular(tarjetaLocal.getTitular())
                .alias(tarjetaLocal.getAlias())
                .esPredeterminada(true)
                .estadoId(1)
                .creacionFecha(tarjetaLocal.getCreacionFecha())
                .mensaje("Tarjeta vinculada exitosamente con Mercado Pago")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TarjetaGuardadaResponse> getUserCards(String arenaId, Long usuarioId) {
        List<TarjetaClientePago> tarjetas = (arenaId != null && !arenaId.isBlank())
                ? tarjetaClientePagoRepository.findByClientePagoArenaIdAndClientePagoArenaUsuarioIdAndEstadoId(arenaId,
                        usuarioId, 1)
                : tarjetaClientePagoRepository.findByClientePagoArenaUsuarioIdAndEstadoId(usuarioId, 1);

        return tarjetas.stream()
                .map(t -> TarjetaGuardadaResponse.builder()
                        .id(t.getId())
                        .clientePagoId(t.getClientePago() != null ? t.getClientePago().getId() : null)
                        .bindingId(t.getMpCardId() != null ? t.getMpCardId()
                                : (t.getTarjetaId() != null ? t.getTarjetaId() : t.getClientePago().getBindingId()))
                        .tarjetaEnmascarada(t.getTarjetaEnmascarada())
                        .franquicia(t.getFranquicia())
                        .expiracion(t.getExpiracion())
                        .titular(t.getTitular())
                        .alias(t.getAlias())
                        .esPredeterminada(Boolean.TRUE.equals(t.getEsPredeterminada()))
                        .estadoId(t.getEstadoId())
                        .creacionFecha(t.getCreacionFecha())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void deleteUserCard(Long tarjetaId, String arenaId, Long usuarioId) {
        TarjetaClientePago tarjeta = (arenaId != null && !arenaId.isBlank())
                ? tarjetaClientePagoRepository
                        .findByIdAndClientePagoArenaIdAndClientePagoArenaUsuarioId(tarjetaId, arenaId, usuarioId)
                        .orElse(null)
                : tarjetaClientePagoRepository.findByIdAndClientePagoArenaUsuarioId(tarjetaId, usuarioId).orElse(null);

        if (tarjeta == null) {
            throw new ResourceNotFoundException("Tarjeta con ID " + tarjetaId + " no encontrada");
        }

        tarjeta.setEstadoId(2); // Desactivada
        tarjetaClientePagoRepository.save(tarjeta);

        if (tarjeta.getMpCardId() != null && tarjeta.getClientePago() != null
                && tarjeta.getClientePago().getMpCustomerId() != null) {
            try {
                mercadoPagoClient.deleteCustomerCard(tarjeta.getClientePago().getMpCustomerId(), tarjeta.getMpCardId(),
                        arenaId, usuarioId);
            } catch (Exception ex) {
                log.warn("No se pudo desvincular tarjeta en Mercado Pago: {}", ex.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void processWebhook(String rawBody, String xSignature, String xRequestId, String dataId) {
        log.info("[MercadoPago Webhook] Recibido payload: {}, xSignature={}, xRequestId={}, dataId={}",
                rawBody, xSignature, xRequestId, dataId);

        // 1. Validar firma HMAC SHA-256 si viene configurada
        String secret = mercadoPagoProperties.getWebhookSecret();
        if (secret != null && !secret.isBlank() && xSignature != null && xRequestId != null) {
            validateWebhookSignature(secret, rawBody, xSignature, xRequestId, dataId);
        }

        // 2. Extraer ID de la orden o pago
        String orderId = dataId;
        String topic = null;
        try {
            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("topic")) {
                    topic = root.get("topic").asText();
                } else if (root.has("type")) {
                    topic = root.get("type").asText();
                }

                if (orderId == null || orderId.isBlank()) {
                    if (root.has("data") && root.get("data").has("id")) {
                        orderId = root.get("data").get("id").asText();
                    } else if (root.has("resource")) {
                        String res = root.get("resource").asText();
                        orderId = res.contains("/") ? res.substring(res.lastIndexOf('/') + 1) : res;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[MercadoPago Webhook] Error parseando JSON de notificaciÃƒÂ³n: {}", ex.getMessage());
        }

        if (orderId == null || orderId.isBlank()) {
            log.warn("[MercadoPago Webhook] NotificaciÃƒÂ³n sin identificador de orden o recurso vÃƒÂ¡lido");
            return;
        }

        // 3. Buscar PedidoPago asociado por mpOrderId, referencia o id
        final String lookupOrderId = orderId;
        PedidoPago pedidoPago = pedidoPagoRepository.findByMpOrderId(lookupOrderId)
                .or(() -> pedidoPagoRepository.findByReferenciaPago(lookupOrderId))
                .or(() -> pedidoPagoRepository.findByMpPaymentId(lookupOrderId))
                .orElse(null);

        if (pedidoPago != null) {
            log.info("🔔 [MercadoPago Webhook] Reconciliando pedido pago #{} (Estado actual: {}) para orden/pago MP {}",
                    pedidoPago.getId(), pedidoPago.getEstadoId(), orderId);
            if (orderId.matches("\\d+")
                    && (pedidoPago.getMpPaymentId() == null || !Integer.valueOf(2).equals(pedidoPago.getEstadoId()))) {
                pedidoPago.setMpPaymentId(orderId);
                pedidoPagoRepository.save(pedidoPago);
            }
            syncAndMapOrderStatus(pedidoPago, pedidoPago.getArenaId(), pedidoPago.getUsuarioId());
        } else {
            // A. Si el evento es un pago o el ID es puramente numérico, consultar
            // getPayment
            boolean isPaymentTopic = (topic != null && (topic.contains("payment") || topic.contains("pago")))
                    || orderId.matches("\\d+");
            if (isPaymentTopic) {
                try {
                    com.beathub.multiarenas.delivery.payment.client.dto.mercadopago.MpPaymentDetailResponse mpPayment = mercadoPagoClient
                            .getPayment(orderId, null, null);
                    if (mpPayment != null && mpPayment.getExternalReference() != null) {
                        PedidoPago porRef = pedidoPagoRepository.findByReferenciaPago(mpPayment.getExternalReference())
                                .orElse(null);
                        if (porRef != null) {
                            log.info(
                                    "🔔 [MercadoPago Webhook] PedidoPago #{} localizado por external_reference de pago: {}",
                                    porRef.getId(), mpPayment.getExternalReference());
                            if (porRef.getMpPaymentId() == null || !Integer.valueOf(2).equals(porRef.getEstadoId())) {
                                porRef.setMpPaymentId(orderId);
                                pedidoPagoRepository.save(porRef);
                            }
                            syncAndMapOrderStatus(porRef, porRef.getArenaId(), porRef.getUsuarioId());
                            return;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[MercadoPago Webhook] No se pudo resolver por payment {}: {}", orderId, ex.getMessage());
                }
            }

            // B. Intentar consultar orden directamente ante MP para obtener
            // external_reference
            try {
                MpOrderResponse mpOrder = mercadoPagoClient.getOrder(orderId, null, null);
                if (mpOrder != null && mpOrder.getExternalReference() != null) {
                    PedidoPago porRef = pedidoPagoRepository.findByReferenciaPago(mpOrder.getExternalReference())
                            .orElse(null);
                    if (porRef != null) {
                        log.info(
                                "🔔 [MercadoPago Webhook] PedidoPago #{} localizado por external_reference de orden: {}",
                                porRef.getId(), mpOrder.getExternalReference());
                        if (porRef.getMpOrderId() == null) {
                            porRef.setMpOrderId(orderId);
                            pedidoPagoRepository.save(porRef);
                        }
                        syncAndMapOrderStatus(porRef, porRef.getArenaId(), porRef.getUsuarioId());
                    }
                }
            } catch (Exception ex) {
                log.warn("[MercadoPago Webhook] Transacción {} no localizada localmente: {}", orderId,
                        ex.getMessage());
            }
        }
    }

    private void validateWebhookSignature(String secret, String rawBody, String xSignature, String xRequestId,
            String dataId) {
        try {
            Map<String, String> parts = new HashMap<>();
            for (String pair : xSignature.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    parts.put(kv[0].trim(), kv[1].trim());
                }
            }

            String ts = parts.get("ts");
            String receivedSignature = parts.get("v1");
            if (ts == null || receivedSignature == null) {
                log.warn("[MercadoPago Webhook] x-signature invÃƒÂ¡lido o incompleto");
                return;
            }

            String resolvedId = dataId != null ? dataId : "";
            if (resolvedId.isBlank() && rawBody != null) {
                JsonNode root = objectMapper.readTree(rawBody);
                if (root.has("data") && root.get("data").has("id")) {
                    resolvedId = root.get("data").get("id").asText();
                }
            }

            String manifest = "id:" + resolvedId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";
            boolean valid = hmacSignatureService.verifySignature(secret, manifest, receivedSignature);
            if (!valid) {
                log.warn("[MercadoPago Webhook] Firma HMAC SHA256 no coincidiÃƒÂ³ con el secreto");
            } else {
                log.info("[MercadoPago Webhook] Firma HMAC SHA256 verificada exitosamente");
            }
        } catch (Exception ex) {
            log.warn("[MercadoPago Webhook] ExcepciÃƒÂ³n verificando firma de webhook: {}", ex.getMessage());
        }
    }

    private ClientePago resolveOrCreateLocalCustomer(String arenaId, Long usuarioId, String email, String nombre,
            String apellido) {
        ClientePago local = clientePagoRepository.findFirstByArenaIdAndArenaUsuarioId(arenaId, usuarioId)
                .or(() -> clientePagoRepository.findFirstByArenaUsuarioId(usuarioId))
                .orElse(null);

        if (local != null && local.getMpCustomerId() != null && !local.getMpCustomerId().isBlank()) {
            return local;
        }

        // Buscar en MP por email
        MpCustomerResponse mpCust = mercadoPagoClient.searchCustomer(email, arenaId, usuarioId);
        if (mpCust == null) {
            mpCust = mercadoPagoClient.createCustomer(MpCustomerRequest.builder()
                    .email(email)
                    .firstName(nombre)
                    .lastName(apellido)
                    .build(), arenaId, usuarioId);
        }

        if (local == null) {
            local = ClientePago.builder()
                    .clienteReferencia("CUST-" + usuarioId)
                    .arenaUsuarioId(usuarioId)
                    .arenaId(arenaId)
                    .mpCustomerId(mpCust.getId())
                    .estadoId(1)
                    .creacionUsuario(usuarioId)
                    .build();
        } else {
            local.setMpCustomerId(mpCust.getId());
        }

        return clientePagoRepository.save(local);
    }

    private String getOrCreatePaymentProfile(String customerId, TarjetaClientePago localCard, String arenaId,
            Long usuarioId) {
        List<MpPaymentProfileResponse> profiles = mercadoPagoClient.getPaymentProfiles(customerId, arenaId, usuarioId);
        for (MpPaymentProfileResponse p : profiles) {
            if (p.getPaymentMethods() != null) {
                for (var pm : p.getPaymentMethods()) {
                    if (pm.getCardId() != null && pm.getCardId().equals(localCard.getMpCardId())) {
                        return p.getId();
                    }
                }
            }
        }

        return createPaymentProfile(customerId, localCard.getMpCardId(), localCard.getPaymentMethodId(), arenaId,
                usuarioId);
    }

    private String createPaymentProfile(String customerId, String cardId, String paymentMethodId, String arenaId,
            Long usuarioId) {
        String pmId = paymentMethodId != null ? paymentMethodId.toLowerCase() : "visa";
        if (pmId.startsWith("deb"))
            pmId = pmId.substring(3);

        MpPaymentProfileRequest req = MpPaymentProfileRequest.builder()
                .description("Perfil Recurrente BeatHub Delivery")
                .paymentMethods(List.of(MpPaymentProfileRequest.ProfilePaymentMethod.builder()
                        .id(pmId)
                        .type("credit_card")
                        .cardId(cardId)
                        .defaultMethod(true)
                        .build()))
                .build();

        MpPaymentProfileResponse resp = mercadoPagoClient.createPaymentProfile(customerId, req, arenaId, usuarioId);
        return resp.getId();
    }

    private int mapMpStatusToEstadoId(String status, String statusDetail) {
        if (status == null) {
            return PedidoPago.ESTADO_INICIADO;
        }
        String s = status.trim().toLowerCase();
        String d = statusDetail != null ? statusDetail.trim().toLowerCase() : "";

        if ("refunded".equals(s) || "charged_back".equals(s) || "partially_refunded".equals(d)) {
            return PedidoPago.ESTADO_REVERSADO; // 25: Reversado / Reembolsado (Flujo 5)
        }
        if ("approved".equals(s) || "processed".equals(s) || "accredited".equals(s)) {
            return PedidoPago.ESTADO_APROBADO; // 23: Aprobado (Flujo 5)
        }
        if ("rejected".equals(s) || "failed".equals(s)) {
            return PedidoPago.ESTADO_RECHAZADO; // 24: Rechazado (Flujo 5)
        }
        if ("cancelled".equals(s)) {
            return PedidoPago.ESTADO_ANULADO; // 26: Anulado (Flujo 5)
        }
        if ("pending".equals(s) || "in_process".equals(s) || "in_mediation".equals(s) || "authorized".equals(s)) {
            return PedidoPago.ESTADO_INICIADO; // 22: Iniciado / Pendiente (Flujo 5)
        }
        return PedidoPago.ESTADO_INICIADO;
    }

    private boolean isStateChangeAllowed(Integer currentEstadoId, int newEstadoId, String rawStatus) {
        if (currentEstadoId == null) {
            return true;
        }
        if (currentEstadoId == newEstadoId) {
            return true;
        }
        // Inmutabilidad de Pagado / Aprobado (23 en Flujo 5 o 2 histórico):
        boolean isApproved = Integer.valueOf(PedidoPago.ESTADO_APROBADO).equals(currentEstadoId)
                || Integer.valueOf(2).equals(currentEstadoId);
        if (isApproved) {
            if (newEstadoId == PedidoPago.ESTADO_REVERSADO || newEstadoId == 25 || newEstadoId == 4) {
                return true;
            }
            log.info(
                    "🛡️ [MercadoPago] Ignorando cambio de estado regresivo para transacción APROBADA: estado actual={} -> nuevo intentado={} ({})",
                    currentEstadoId, newEstadoId, rawStatus);
            return false;
        }
        // Transacciones ya Reversadas / Reembolsadas (25 en Flujo 5 o 4 histórico): estado terminal
        boolean isRefunded = Integer.valueOf(PedidoPago.ESTADO_REVERSADO).equals(currentEstadoId)
                || Integer.valueOf(25).equals(currentEstadoId)
                || Integer.valueOf(4).equals(currentEstadoId);
        if (isRefunded) {
            log.info("🛡️ [MercadoPago] Ignorando cambio de estado para transacción ya REVERSADA/REEMBOLSADA: estado actual={} -> nuevo intentado={} ({})",
                    currentEstadoId, newEstadoId, rawStatus);
            return false;
        }
        return true;
    }

    private void applyStateTransition(PedidoPago pedidoPago, int newEstadoId, String rawStatus, String statusDetail,
            String resolvedMedioPago) {
        Integer currentEstadoId = pedidoPago.getEstadoId();

        if (!isStateChangeAllowed(currentEstadoId, newEstadoId, rawStatus)) {
            // Guardar solo metadatos si no existían (authCode, actionCode, etc.) sin alterar el estado
            pedidoPagoRepository.save(pedidoPago);
            return;
        }

        if (newEstadoId == PedidoPago.ESTADO_APROBADO || newEstadoId == 2) {
            onPaymentApproved(pedidoPago, "Pago aprobado por Mercado Pago", pedidoPago.getAuthCode(),
                    "MERCADOPAGO", resolvedMedioPago);
        } else if (newEstadoId == PedidoPago.ESTADO_REVERSADO || newEstadoId == 4) {
            pedidoPago.setEstadoId(PedidoPago.ESTADO_REVERSADO);
            pedidoPago.setErrorMessage(statusDetail != null ? statusDetail : "Reembolso procesado en Mercado Pago");
            pedidoPagoRepository.save(pedidoPago);
            try {
                orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), PedidoPago.ESTADO_REVERSADO, "Pedido reembolsado/reversado en Mercado Pago", null);
            } catch (Exception ex) {
                log.warn("No se pudo notificar reembolso a Ordering para pedido #{}: {}", pedidoPago.getPedidoId(), ex.getMessage());
            }
        } else if (newEstadoId == PedidoPago.ESTADO_RECHAZADO || newEstadoId == 3) {
            pedidoPago.setEstadoId(PedidoPago.ESTADO_RECHAZADO);
            pedidoPago.setErrorMessage(statusDetail != null ? statusDetail : "Pago rechazado por Mercado Pago");
            pedidoPagoRepository.save(pedidoPago);
        } else if (newEstadoId == PedidoPago.ESTADO_ANULADO || newEstadoId == 5) {
            pedidoPago.setEstadoId(PedidoPago.ESTADO_ANULADO);
            pedidoPago.setErrorMessage(statusDetail != null ? statusDetail : "Pago cancelado o anulado por Mercado Pago");
            pedidoPagoRepository.save(pedidoPago);
        } else {
            // PedidoPago.ESTADO_INICIADO (22)
            pedidoPago.setEstadoId(PedidoPago.ESTADO_INICIADO);
            pedidoPagoRepository.save(pedidoPago);
        }
    }

    private void onPaymentApproved(PedidoPago pedidoPago, String detailMessage, String autorizacion, String terminal) {
        onPaymentApproved(pedidoPago, detailMessage, autorizacion, terminal, "Tarjeta de crédito o débito");
    }

    private void onPaymentApproved(PedidoPago pedidoPago, String detailMessage, String autorizacion, String terminal,
            String friendlyPaymentMethod) {
        boolean alreadyApproved = Integer.valueOf(PedidoPago.ESTADO_APROBADO).equals(pedidoPago.getEstadoId())
                || Integer.valueOf(2).equals(pedidoPago.getEstadoId());
        if (alreadyApproved) {
            return;
        }

        pedidoPago.setEstadoId(PedidoPago.ESTADO_APROBADO); // 23: Aprobado (Flujo 5)
        pedidoPago.setFechaPago(LocalDateTime.now());
        if (autorizacion != null && !autorizacion.isBlank()) {
            pedidoPago.setAuthCode(autorizacion);
        }
        pedidoPagoRepository.save(pedidoPago);

        String effectiveAuth = (autorizacion != null && !autorizacion.isBlank())
                ? autorizacion
                : pedidoPago.getAuthCode();

        String effectivePaymentMethod = (friendlyPaymentMethod != null && !friendlyPaymentMethod.isBlank())
                ? friendlyPaymentMethod
                : "Tarjeta de crédito o débito";

        if (pedidoPago.getGrupoPagoUsuario() != null) {
            GrupoPagoUsuario gpu = pedidoPago.getGrupoPagoUsuario();
            BigDecimal pagadoPrevio = gpu.getMontoPagado() != null ? gpu.getMontoPagado() : BigDecimal.ZERO;
            gpu.setMontoPagado(pagadoPrevio.add(pedidoPago.getMonto()));
            if (gpu.getMontoAsignado() != null && gpu.getMontoPagado().compareTo(gpu.getMontoAsignado()) >= 0) {
                gpu.setEstadoId(2);
            }
            grupoPagoUsuarioRepository.save(gpu);

            GrupoPago gp = gpu.getGrupoPago();
            if (gp != null) {
                BigDecimal totalPagadoPrevio = gp.getTotalPagado() != null ? gp.getTotalPagado() : BigDecimal.ZERO;
                BigDecimal nuevoTotalPagado = totalPagadoPrevio.add(pedidoPago.getMonto());
                gp.setTotalPagado(nuevoTotalPagado);

                BigDecimal valorTotal = gp.getValorTotal() != null ? gp.getValorTotal() : BigDecimal.ZERO;
                BigDecimal nuevoSaldo = valorTotal.subtract(nuevoTotalPagado).max(BigDecimal.ZERO);
                gp.setSaldoPendiente(nuevoSaldo);

                if (nuevoSaldo.compareTo(BigDecimal.ZERO) <= 0) {
                    gp.setEstadoId(2);
                    String msg = "Vaca/Pago grupal 100% completado con Mercado Pago (" + detailMessage + ")";
                    orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), PedidoPago.ESTADO_APROBADO, msg, null, effectiveAuth, terminal,
                            pedidoPago.getMonto(), effectivePaymentMethod);
                    if (notificationServiceClient != null) {
                        try {
                            notificationServiceClient.notificarVacaCompletada(
                                    gp.getArenaId() != null && gp.getArenaId().matches("\\d+")
                                             ? Long.parseLong(gp.getArenaId())
                                            : 1L,
                                    pedidoPago.getUsuarioId() != null ? pedidoPago.getUsuarioId() : gpu.getUsuarioId(),
                                    pedidoPago.getPedidoId(),
                                    gp.getNombre(),
                                    gp.getValorTotal());
                        } catch (Exception ex) {
                            log.warn("Error enviando notificación vaca completada: {}", ex.getMessage());
                        }
                    }
                }
                grupoPagoRepository.save(gp);
            }
        } else {
            // Notificar a Ordering (estado 23: Aprobado / Pagado)
            orderingClient.updateOrderStatus(pedidoPago.getPedidoId(), PedidoPago.ESTADO_APROBADO, detailMessage, null, effectiveAuth, terminal,
                    pedidoPago.getMonto(), effectivePaymentMethod);
        }
    }

    private String mapEstadoNombre(Integer estadoId) {
        if (estadoId == null)
            return "DESCONOCIDO";
        return switch (estadoId) {
            case 22, 1 -> "INICIADO";
            case 23, 2 -> "APROBADO";
            case 24, 3 -> "RECHAZADO";
            case 25, 4 -> "REVERSADO";
            case 26, 5 -> "ANULADO";
            default -> "ESTADO_" + estadoId;
        };
    }
}
