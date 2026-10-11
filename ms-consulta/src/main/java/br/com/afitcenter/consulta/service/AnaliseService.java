package br.com.afitcenter.consulta.service;

import br.com.afitcenter.consulta.model.dto.Dtos.AnaliseRequest;
import br.com.afitcenter.consulta.model.dto.Dtos.AnaliseResponse;
import br.com.afitcenter.consulta.model.entity.Analise;
import br.com.afitcenter.consulta.repository.AnaliseRepository;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Análises do nutricionista na consulta (requisito 2). Exclusão lógica: é prontuário. */
@Service
@RequiredArgsConstructor
public class AnaliseService {

    private final AnaliseRepository repository;
    private final ConsultaService consultaService;

    @Transactional(readOnly = true)
    public List<AnaliseResponse> listar(UUID nutricionistaId, UUID consultaId) {
        consultaService.entidade(nutricionistaId, consultaId);
        return repository.findAllByConsultaIdAndDeletadoEmIsNullOrderByCriadoEm(consultaId).stream()
                .map(AnaliseResponse::de).toList();
    }

    @Transactional
    public AnaliseResponse registrar(UUID nutricionistaId, UUID consultaId, AnaliseRequest dados) {
        consultaService.entidade(nutricionistaId, consultaId);
        return AnaliseResponse.de(repository.save(new Analise(consultaId, dados.categoria(), dados.descricao().trim())));
    }

    @Transactional
    public AnaliseResponse atualizar(UUID nutricionistaId, UUID consultaId, UUID id, AnaliseRequest dados) {
        Analise analise = entidade(nutricionistaId, consultaId, id);
        analise.atualizar(dados.categoria(), dados.descricao().trim());
        return AnaliseResponse.de(repository.saveAndFlush(analise));
    }

    @Transactional
    public void excluir(UUID nutricionistaId, UUID consultaId, UUID id) {
        entidade(nutricionistaId, consultaId, id).excluir();
    }

    private Analise entidade(UUID nutricionistaId, UUID consultaId, UUID id) {
        consultaService.entidade(nutricionistaId, consultaId);
        return repository.findByIdAndConsultaIdAndDeletadoEmIsNull(id, consultaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Análise não encontrada."));
    }
}
