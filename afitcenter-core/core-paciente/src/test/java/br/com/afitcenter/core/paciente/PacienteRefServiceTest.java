package br.com.afitcenter.core.paciente;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacienteRefServiceTest {

    private final PacienteRefRepository repository = mock(PacienteRefRepository.class);
    private final PacienteInternoClient client = mock(PacienteInternoClient.class);
    private final PacienteRefService service = new PacienteRefService(repository, client);

    private final UUID nutri = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();

    @Test
    void usaAReplicaLocalSemChamarOMsPaciente() {
        when(repository.buscar(pacienteId)).thenReturn(Optional.of(ref(nutri, false)));

        assertThat(service.exigirDoNutricionista(pacienteId, nutri).id()).isEqualTo(pacienteId);
        verify(client, never()).buscar(any());
    }

    @Test
    void pacienteDeOutroNutricionistaRespondeComoInexistente() {
        when(repository.buscar(pacienteId)).thenReturn(Optional.of(ref(UUID.randomUUID(), false)));

        assertThatThrownBy(() -> service.exigirDoNutricionista(pacienteId, nutri))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void pacienteExcluidoRespondeComoInexistente() {
        when(repository.buscar(pacienteId)).thenReturn(Optional.of(ref(nutri, true)));

        assertThatThrownBy(() -> service.exigirDoNutricionista(pacienteId, nutri))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void semReplicaBuscaNoMsPacienteEGravaLocalmente() {
        PacienteRef remoto = ref(nutri, false);
        when(repository.buscar(pacienteId)).thenReturn(Optional.empty());
        when(client.buscar(pacienteId)).thenReturn(remoto);

        assertThat(service.exigirDoNutricionista(pacienteId, nutri)).isEqualTo(remoto);
        verify(repository).aplicar(remoto);
    }

    @Test
    void inexistenteNoMsPacienteResponde404() {
        when(repository.buscar(pacienteId)).thenReturn(Optional.empty());
        when(client.buscar(pacienteId)).thenThrow(new FeignException.NotFound("nao encontrado",
                Request.create(Request.HttpMethod.GET, "/internal/pacientes/x", Map.of(), null,
                        StandardCharsets.UTF_8, null), null, Map.of()));

        assertThatThrownBy(() -> service.exigirDoNutricionista(pacienteId, nutri))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repository, never()).aplicar(any());
    }

    private PacienteRef ref(UUID dono, boolean excluido) {
        return new PacienteRef(pacienteId, dono, "Paciente", "F", LocalDate.of(1990, 1, 1), "ATIVO", excluido, 1);
    }
}
