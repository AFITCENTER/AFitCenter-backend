package br.com.afitcenter.paciente.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.paciente.model.dto.Dtos.CondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.CondicaoResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.TipoCondicaoResponse;
import br.com.afitcenter.paciente.model.entity.Condicao;
import br.com.afitcenter.paciente.model.entity.TipoCondicao;
import br.com.afitcenter.paciente.repository.CondicaoRepository;
import br.com.afitcenter.paciente.repository.TipoCondicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Catálogo de condições: o do sistema (só leitura) mais as condições próprias do nutricionista. */
@Service
@RequiredArgsConstructor
public class CondicaoService {

    private final TipoCondicaoRepository tipoRepository;
    private final CondicaoRepository repository;

    @Transactional(readOnly = true)
    public List<TipoCondicaoResponse> tipos() {
        return tipoRepository.findAllByOrderByNome().stream().map(TipoCondicaoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<CondicaoResponse> listar(UUID nutricionistaId, Integer tipoId) {
        return repository.findVisiveis(nutricionistaId, tipoId).stream().map(CondicaoResponse::de).toList();
    }

    @Transactional
    public CondicaoResponse criar(UUID nutricionistaId, CondicaoRequest dados) {
        Condicao condicao = new Condicao(tipo(dados.tipoCondicaoId()), nutricionistaId, dados.nome().trim(),
                dados.descricao());
        return CondicaoResponse.de(repository.saveAndFlush(condicao));
    }

    @Transactional
    public CondicaoResponse atualizar(UUID nutricionistaId, Integer id, CondicaoRequest dados) {
        Condicao condicao = propria(nutricionistaId, id);
        condicao.atualizar(tipo(dados.tipoCondicaoId()), dados.nome().trim(), dados.descricao());
        return CondicaoResponse.de(repository.saveAndFlush(condicao));
    }

    /** Recusada com 409 se algum paciente usa a condição (chave estrangeira). */
    @Transactional
    public void excluir(UUID nutricionistaId, Integer id) {
        repository.delete(propria(nutricionistaId, id));
        repository.flush();
    }

    @Transactional(readOnly = true)
    public Condicao visivel(UUID nutricionistaId, Integer id) {
        return repository.findVisivel(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Condição não encontrada."));
    }

    private Condicao propria(UUID nutricionistaId, Integer id) {
        Condicao condicao = visivel(nutricionistaId, id);
        if (condicao.isDoSistema())
            throw new RegraNegocioException("Condições do catálogo do sistema não podem ser alteradas.");
        return condicao;
    }

    private TipoCondicao tipo(Integer id) {
        return tipoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Tipo de condição inexistente."));
    }
}
