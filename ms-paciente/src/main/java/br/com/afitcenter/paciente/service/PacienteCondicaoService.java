package br.com.afitcenter.paciente.service;

import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.paciente.model.dto.Dtos.AtualizarPacienteCondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteCondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteCondicaoResponse;
import br.com.afitcenter.paciente.model.entity.Condicao;
import br.com.afitcenter.paciente.model.entity.PacienteCondicao;
import br.com.afitcenter.paciente.repository.PacienteCondicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Condições (alergias, intolerâncias, doenças, restrições físicas) registradas para um paciente. */
@Service
@RequiredArgsConstructor
public class PacienteCondicaoService {

    private final PacienteCondicaoRepository repository;
    private final PacienteService pacienteService;
    private final CondicaoService condicaoService;

    @Transactional(readOnly = true)
    public List<PacienteCondicaoResponse> listar(UUID nutricionistaId, UUID pacienteId) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        return repository.findAllByPacienteIdOrderByCriadoEm(pacienteId).stream()
                .map(PacienteCondicaoResponse::de).toList();
    }

    @Transactional
    public PacienteCondicaoResponse adicionar(UUID nutricionistaId, UUID pacienteId, PacienteCondicaoRequest dados) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        Condicao condicao = condicaoService.visivel(nutricionistaId, dados.condicaoId());
        if (repository.existsByPacienteIdAndCondicaoId(pacienteId, condicao.getId()))
            throw new ConflitoException("Esta condição já está registrada para o paciente.");

        PacienteCondicao vinculo = new PacienteCondicao(pacienteId, condicao);
        vinculo.atualizar(dados.gravidade(), dados.desde(), dados.ativa() == null || dados.ativa(), dados.observacao());
        return PacienteCondicaoResponse.de(repository.save(vinculo));
    }

    @Transactional
    public PacienteCondicaoResponse atualizar(UUID nutricionistaId, UUID pacienteId, UUID id,
                                              AtualizarPacienteCondicaoRequest dados) {
        PacienteCondicao vinculo = entidade(nutricionistaId, pacienteId, id);
        vinculo.atualizar(dados.gravidade(), dados.desde(), dados.ativa(), dados.observacao());
        return PacienteCondicaoResponse.de(vinculo);
    }

    /** Para "não tem mais a condição", prefira marcar {@code ativa=false}: o histórico fica registrado. */
    @Transactional
    public void remover(UUID nutricionistaId, UUID pacienteId, UUID id) {
        repository.delete(entidade(nutricionistaId, pacienteId, id));
    }

    /** Ids das condições ativas: usados pelo ms-dieta para alertar alimentos restritos. */
    @Transactional(readOnly = true)
    public List<Integer> condicoesAtivas(UUID pacienteId) {
        return repository.findCondicaoIdsAtivas(pacienteId);
    }

    private PacienteCondicao entidade(UUID nutricionistaId, UUID pacienteId, UUID id) {
        pacienteService.entidade(nutricionistaId, pacienteId);
        return repository.findByIdAndPacienteId(id, pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Condição do paciente não encontrada."));
    }
}
