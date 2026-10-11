package br.com.afitcenter.avaliacao.integracao;

import br.com.afitcenter.avaliacao.model.dto.Dtos.ConsultaInterna;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Valida {@code consulta_id} de avaliações e exames (não existe FK entre serviços). Outras falhas do
 * ms-consulta sobem como {@link FeignException} e viram 502.
 */
@Service
@RequiredArgsConstructor
public class ConsultaVinculoService {

    private final ConsultaInternoClient client;

    /**
     * A consulta existe e é do mesmo paciente e nutricionista. Caso contrário responde 404, sem
     * revelar se a consulta existe para outro paciente ou nutricionista.
     */
    public void exigirDoPaciente(UUID consultaId, UUID pacienteId, UUID nutricionistaId) {
        ConsultaInterna consulta = buscar(consultaId);
        if (consulta == null || !nutricionistaId.equals(consulta.nutricionistaId())
                || !pacienteId.equals(consulta.pacienteId()))
            throw new RecursoNaoEncontradoException("Consulta não encontrada.");
    }

    private ConsultaInterna buscar(UUID consultaId) {
        try {
            return client.buscar(consultaId);
        } catch (FeignException.NotFound e) {
            return null;
        }
    }
}
