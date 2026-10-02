package com.beathub.multiarenas.delivery.payment.controller;

import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.ApiResponse;
import com.beathub.multiarenas.delivery.payment.dto.response.GrupoPagoResponse;
import com.beathub.multiarenas.delivery.payment.dto.response.PaymentInitResponse;
import com.beathub.multiarenas.delivery.payment.security.SecurityUtils;
import com.beathub.multiarenas.delivery.payment.service.GrupoPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/grupos")
@RequiredArgsConstructor
@Tag(name = "Grupos de Pago (Vaca / Split Payment)", description = "Gestión de pagos grupales, división de cuentas y aportes compartidos")
public class GrupoPagoController {

    private final GrupoPagoService grupoPagoService;

    @PostMapping
    @Operation(summary = "Crear un nuevo grupo de pago (Vaca) para un pedido")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> crearGrupo(
            @Valid @RequestBody CrearGrupoPagoRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);
        String authToken = extractAuthToken(httpRequest);

        GrupoPagoResponse response = grupoPagoService.crearGrupo(request, arenaId, usuarioId, authToken);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Grupo de pago (Vaca) creado exitosamente"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar detalle de un grupo de pago por su ID")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> obtenerPorId(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.obtenerPorId(id, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/codigo/{codigoUnico}")
    @Operation(summary = "Consultar grupo de pago por su código único de invitación (VACA-XXXXXX)")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> obtenerPorCodigo(
            @PathVariable String codigoUnico,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = SecurityUtils.getCurrentUsuarioId().orElse(null);
        GrupoPagoResponse response = grupoPagoService.obtenerPorCodigo(codigoUnico, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/pedido/{pedidoId}")
    @Operation(summary = "Consultar grupo de pago asociado a un pedido")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> obtenerPorPedidoId(
            @PathVariable Long pedidoId,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.obtenerPorPedidoId(pedidoId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/mis-grupos")
    @Operation(summary = "Listar todos los grupos de pago donde participa el usuario autenticado")
    public ResponseEntity<ApiResponse<List<GrupoPagoResponse>>> listarMisGrupos(
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        List<GrupoPagoResponse> response = grupoPagoService.listarMisGrupos(usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/unirse")
    @Operation(summary = "Unirse a un grupo de pago existente mediante ID")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> unirsePorId(
            @PathVariable Long id,
            @RequestBody(required = false) UnirseGrupoRequest request,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.unirseAGrupoPorId(id, request, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Te has unido al grupo de pago exitosamente"));
    }

    @PostMapping("/codigo/{codigoUnico}/unirse")
    @Operation(summary = "Unirse a un grupo de pago existente mediante Código Único")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> unirsePorCodigo(
            @PathVariable String codigoUnico,
            @RequestBody(required = false) UnirseGrupoRequest request,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.unirseAGrupo(codigoUnico, request, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Te has unido al grupo de pago exitosamente"));
    }

    @PostMapping("/{id}/participantes")
    @Operation(summary = "Agregar o actualizar cuota de un participante (Sólo líder)")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> agregarParticipante(
            @PathVariable Long id,
            @Valid @RequestBody AgregarParticipanteRequest request,
            HttpServletRequest httpRequest
    ) {
        Long usuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.agregarParticipante(id, request, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Participante asignado exitosamente"));
    }

    @DeleteMapping("/{id}/participantes/{usuarioIdAEliminar}")
    @Operation(summary = "Retirar a un participante del grupo (Sólo líder, sin pagos previos)")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> eliminarParticipante(
            @PathVariable Long id,
            @PathVariable Long usuarioIdAEliminar,
            HttpServletRequest httpRequest
    ) {
        Long currentUsuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.eliminarParticipante(id, usuarioIdAEliminar, currentUsuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Participante retirado del grupo exitosamente"));
    }

    @PostMapping("/{id}/recalcular-cuotas")
    @Operation(summary = "Dividir cuotas equitativamente entre los integrantes (Sólo líder)")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> recalcularCuotas(
            @PathVariable Long id,
            @RequestBody(required = false) RecalcularCuotasRequest request,
            HttpServletRequest httpRequest
    ) {
        Long currentUsuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.recalcularCuotas(id, request, currentUsuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cuotas recalculadas exitosamente"));
    }

    @PostMapping("/{id}/pagar")
    @Operation(summary = "Iniciar pago de cuota / aporte para este grupo", description = "Genera transacción de Credibanco para cubrir la parte correspondiente del usuario")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> pagarCuotaGrupo(
            @PathVariable Long id,
            @Valid @RequestBody PagarCuotaGrupoRequest request,
            HttpServletRequest httpRequest
    ) {
        String arenaId = resolveArenaId(httpRequest);
        Long usuarioId = resolveUsuarioId(httpRequest);

        PaymentInitResponse response = grupoPagoService.pagarCuotaGrupo(id, request, arenaId, usuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Sesión de pago generada exitosamente"));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar grupo de pago (Sólo líder, si no hay pagos registrados)")
    public ResponseEntity<ApiResponse<GrupoPagoResponse>> cancelarGrupo(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        Long currentUsuarioId = resolveUsuarioId(httpRequest);
        GrupoPagoResponse response = grupoPagoService.cancelarGrupo(id, currentUsuarioId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Grupo de pago cancelado exitosamente"));
    }

    private String resolveArenaId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-Arena-Id");
        if (attr != null) return attr.toString();
        return SecurityUtils.getCurrentArenaId().map(String::valueOf).orElse(request.getHeader("X-Arena-Id"));
    }

    private Long resolveUsuarioId(HttpServletRequest request) {
        Object attr = request.getAttribute("X-User-Id");
        if (attr instanceof Long id) return id;
        if (attr instanceof Number num) return num.longValue();
        return SecurityUtils.getCurrentUsuarioId().orElse(1L);
    }

    private String extractAuthToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader : null;
    }
}
