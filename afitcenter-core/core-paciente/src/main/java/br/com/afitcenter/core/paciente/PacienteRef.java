package br.com.afitcenter.core.paciente;

import br.com.afitcenter.core.mensageria.evento.PacienteEvento;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Dados mínimos do paciente replicados nos serviços consumidores. É também o formato de
 * {@code GET /internal/pacientes/{id}} do ms-paciente.
 */
public record PacienteRef(
        UUID id,
        UUID nutricionistaId,
        String nome,
        String sexo,
        LocalDate dataNascimento,
        String status,
        boolean excluido,
        long versao
) {

    public static PacienteRef de(PacienteEvento evento) {
        return new PacienteRef(evento.pacienteId(), evento.nutricionistaId(), evento.nome(), evento.sexo(),
                evento.dataNascimento(), evento.status(), evento.excluido(), evento.versao());
    }
}
