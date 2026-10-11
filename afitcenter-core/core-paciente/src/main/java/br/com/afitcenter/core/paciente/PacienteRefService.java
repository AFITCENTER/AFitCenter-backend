package br.com.afitcenter.core.paciente;

import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Garante que um paciente existe e pertence ao nutricionista da requisição, usando a réplica local.
 *
 * <p>O evento de criação leva alguns instantes para chegar. Se o nutricionista cadastrar um paciente
 * e marcar a consulta logo em seguida, a réplica ainda pode estar vazia: nesse caso o paciente é
 * buscado no ms-paciente e gravado na réplica (read-through).</p>
 */
@Slf4j
public class PacienteRefService {

    private final PacienteRefRepository repository;
    private final PacienteInternoClient client;

    public PacienteRefService(PacienteRefRepository repository, PacienteInternoClient client) {
        this.repository = repository;
        this.client = client;
    }

    /**
     * Paciente ativo (não excluído) do nutricionista. Paciente de outro nutricionista responde 404,
     * como se não existisse.
     */
    public PacienteRef exigirDoNutricionista(UUID pacienteId, UUID nutricionistaId) {
        PacienteRef ref = repository.buscar(pacienteId).orElseGet(() -> sincronizar(pacienteId));
        if (ref == null || ref.excluido() || !ref.nutricionistaId().equals(nutricionistaId))
            throw new RecursoNaoEncontradoException("Paciente não encontrado.");
        return ref;
    }

    /** Nomes para listagens (agenda, relatórios), sem chamadas remotas. */
    public Map<UUID, PacienteRef> buscarTodos(Collection<UUID> ids) {
        return repository.buscarTodos(ids).stream().collect(Collectors.toMap(PacienteRef::id, Function.identity()));
    }

    public void aplicar(PacienteEvento evento) {
        boolean alterou = repository.aplicar(PacienteRef.de(evento));
        log.debug("Evento {} do paciente {} (versão {}) {}.", evento.tipo(), evento.pacienteId(), evento.versao(),
                alterou ? "aplicado" : "ignorado por ser antigo");
    }

    private PacienteRef sincronizar(UUID pacienteId) {
        try {
            PacienteRef remoto = client.buscar(pacienteId);
            repository.aplicar(remoto);
            return remoto;
        } catch (FeignException.NotFound e) {
            return null;
        }
    }
}
