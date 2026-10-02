package com.beathub.multiarenas.delivery.payment.service;

import com.beathub.multiarenas.delivery.payment.dto.request.*;
import com.beathub.multiarenas.delivery.payment.dto.response.GrupoPagoResponse;
import com.beathub.multiarenas.delivery.payment.dto.response.PaymentInitResponse;

import java.util.List;

public interface GrupoPagoService {

    GrupoPagoResponse crearGrupo(CrearGrupoPagoRequest request, String arenaId, Long usuarioId, String authToken);

    GrupoPagoResponse obtenerPorId(Long id, Long currentUsuarioId);

    GrupoPagoResponse obtenerPorCodigo(String codigoUnico, Long currentUsuarioId);

    GrupoPagoResponse obtenerPorPedidoId(Long pedidoId, Long currentUsuarioId);

    List<GrupoPagoResponse> listarMisGrupos(Long usuarioId);

    GrupoPagoResponse unirseAGrupo(String codigoUnico, UnirseGrupoRequest request, Long usuarioId);

    GrupoPagoResponse unirseAGrupoPorId(Long grupoPagoId, UnirseGrupoRequest request, Long usuarioId);

    GrupoPagoResponse agregarParticipante(Long grupoPagoId, AgregarParticipanteRequest request, Long currentUsuarioId);

    GrupoPagoResponse eliminarParticipante(Long grupoPagoId, Long usuarioIdAEliminar, Long currentUsuarioId);

    GrupoPagoResponse recalcularCuotas(Long grupoPagoId, RecalcularCuotasRequest request, Long currentUsuarioId);

    PaymentInitResponse pagarCuotaGrupo(Long grupoPagoId, PagarCuotaGrupoRequest request, String arenaId, Long usuarioId);

    GrupoPagoResponse cancelarGrupo(Long grupoPagoId, Long currentUsuarioId);
}
