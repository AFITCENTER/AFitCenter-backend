package br.com.afitcenter.consulta.replica;

import br.com.afitcenter.core.mensageria.evento.ConsultorioEvento;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Réplica dos consultórios: atualizada pelos eventos {@code consultorio.*} do ms-nutricionista, com
 * busca síncrona quando o evento ainda não chegou (mesmo padrão da réplica de paciente).
 */
@Service
@RequiredArgsConstructor
public class ConsultorioRefService {

    public static final String FILA = "ms-consulta.consultorio";

    private final ConsultorioRefRepository repository;
    private final ConsultorioInternoClient client;

    /** Consultório ativo do nutricionista; de outro nutricionista responde 404. */
    public ConsultorioRef exigirAtivoDoNutricionista(UUID consultorioId, UUID nutricionistaId) {
        ConsultorioRef ref = repository.buscar(consultorioId).orElseGet(() -> sincronizar(consultorioId));
        if (ref == null || !ref.ativo() || !ref.nutricionistaId().equals(nutricionistaId))
            throw new RecursoNaoEncontradoException("Consultório não encontrado.");
        return ref;
    }

    public Map<UUID, ConsultorioRef> buscarTodos(Collection<UUID> ids) {
        return repository.buscarTodos(ids).stream().collect(Collectors.toMap(ConsultorioRef::id, Function.identity()));
    }

    @RabbitListener(queues = FILA)
    public void receber(ConsultorioEvento evento) {
        if (evento == null || evento.consultorioId() == null || evento.nutricionistaId() == null)
            throw new IllegalArgumentException("Evento de consultório incompleto.");
        repository.aplicar(ConsultorioRef.de(evento));
    }

    private ConsultorioRef sincronizar(UUID id) {
        try {
            ConsultorioRef remoto = client.buscar(id);
            repository.aplicar(remoto);
            return remoto;
        } catch (FeignException.NotFound e) {
            return null;
        }
    }
}
