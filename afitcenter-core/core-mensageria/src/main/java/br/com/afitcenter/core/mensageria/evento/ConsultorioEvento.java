package br.com.afitcenter.core.mensageria.evento;

import java.time.Instant;
import java.util.UUID;

/** Estado atual do consultório após uma alteração. Mesma regra de versão do {@link PacienteEvento}. */
public record ConsultorioEvento(
        UUID eventoId,
        TipoAlteracao tipo,
        Instant ocorridoEm,
        UUID consultorioId,
        UUID nutricionistaId,
        String nome,
        boolean ativo,
        long versao
) {
}
