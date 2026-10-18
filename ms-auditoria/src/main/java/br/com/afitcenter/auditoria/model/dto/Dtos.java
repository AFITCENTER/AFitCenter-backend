package br.com.afitcenter.auditoria.model.dto;

import br.com.afitcenter.auditoria.model.entity.RegistroAuditoria;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Contratos HTTP do ms-auditoria. */
public final class Dtos {

    private Dtos() {
    }

    public record RegistroResponse(
            UUID id,
            Instant ocorridoEm,
            String servico,
            String acao,
            String recurso,
            String recursoId,
            UUID pacienteId,
            UUID nutricionistaId,
            UUID usuarioId,
            String papel,
            String metodo,
            String rota,
            Integer status,
            String ip,
            Map<String, String> detalhes
    ) {

        public static RegistroResponse de(RegistroAuditoria r) {
            return de(r, true);
        }

        /** {@code mostrarIp=false} esconde o IP de terceiros (administrador, outro usuário) na visão do nutricionista. */
        public static RegistroResponse de(RegistroAuditoria r, boolean mostrarIp) {
            return new RegistroResponse(r.getId(), r.getOcorridoEm(), r.getServico(), r.getAcao(), r.getRecurso(),
                    r.getRecursoId(), r.getPacienteId(), r.getNutricionistaId(), r.getUsuarioId(), r.getPapel(),
                    r.getMetodo(), r.getRota(), r.getStatus(), mostrarIp ? r.getIp() : null, r.getDetalhes());
        }
    }

    /** Filtros da consulta. Todos opcionais; {@code fim} é exclusivo. */
    public record FiltroRegistros(
            Instant inicio,
            Instant fim,
            UUID nutricionistaId,
            UUID pacienteId,
            UUID usuarioId,
            String servico,
            String recurso,
            String recursoId,
            String acao,
            String ip
    ) {
    }
}
