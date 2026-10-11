package br.com.afitcenter.consulta.replica;

import br.com.afitcenter.core.mensageria.evento.ConsultorioEvento;

import java.util.UUID;

/** Réplica mínima do consultório (dono: ms-nutricionista). Também é o formato de GET /internal/consultorios/{id}. */
public record ConsultorioRef(UUID id, UUID nutricionistaId, String nome, boolean ativo, long versao) {

    public static ConsultorioRef de(ConsultorioEvento evento) {
        return new ConsultorioRef(evento.consultorioId(), evento.nutricionistaId(), evento.nome(), evento.ativo(),
                evento.versao());
    }
}
