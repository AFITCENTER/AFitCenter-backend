package br.com.afitcenter.core.mensageria.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado atual do paciente após uma alteração (event-carried state transfer).
 *
 * <p>Os serviços que referenciam pacientes mantêm uma réplica mínima ({@code paciente_ref}).
 * {@code versao} cresce a cada alteração: o consumidor ignora eventos mais antigos que a réplica,
 * então reentregas e mensagens fora de ordem não sobrescrevem dados novos.</p>
 */
public record PacienteEvento(
        UUID eventoId,
        TipoAlteracao tipo,
        Instant ocorridoEm,
        UUID pacienteId,
        UUID nutricionistaId,
        String nome,
        String sexo,
        LocalDate dataNascimento,
        String status,
        boolean excluido,
        long versao
) {
}
