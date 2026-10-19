package br.com.afitcenter.core.mensageria.evento;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Um registro da trilha de auditoria: quem fez o quê, em qual recurso, quando e de onde.
 *
 * <p>Nunca carrega dados pessoais do paciente (nome, CPF, conteúdo clínico): só ids, rota e resultado.
 * Query string e corpo da requisição ficam de fora pelo mesmo motivo.</p>
 *
 * @param servico         serviço que registrou ({@code spring.application.name})
 * @param acao            {@code LEITURA}, {@code CRIACAO}, {@code ALTERACAO}, {@code EXCLUSAO}, {@code LOGIN}...
 * @param recurso         raiz do agregado na rota ({@code pacientes}, {@code consultas}...) ou nome livre
 * @param recursoId       id do recurso (texto: catálogos usam inteiros)
 * @param pacienteId      paciente cujo prontuário foi tocado, quando houver
 * @param nutricionistaId tenant; nulo em ações de administrador, anônimas ou sem tenant
 * @param usuarioId       quem agiu; nulo para anônimo ou sistema
 * @param papel           {@code NUTRICIONISTA}, {@code ADMIN}, {@code SISTEMA}, {@code ANONIMO}...
 * @param metodo          método HTTP (nulo em eventos do sistema)
 * @param rota            padrão da rota ({@code /v1/consultas/{id}/status}), sem valores
 * @param status          status HTTP da resposta (nulo em eventos do sistema)
 * @param detalhes        variáveis da rota e informações extras, sem dados pessoais
 */
public record AuditoriaEvento(
        UUID eventoId,
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

    public AuditoriaEvento {
        detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }
}
