package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.dieta.model.dto.Dtos.RefeicaoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.RefeicaoResponse;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.repository.RefeicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Refeições da dieta. Exclusão física: itens e substitutos vão junto (ON DELETE CASCADE). */
@Service
@RequiredArgsConstructor
public class RefeicaoService {

    private final RefeicaoRepository repository;
    private final DietaService dietaService;
    private final ConteudoDietaService conteudoService;

    @Transactional(readOnly = true)
    public List<RefeicaoResponse> listar(UUID nutricionistaId, UUID dietaId) {
        dietaService.entidade(nutricionistaId, dietaId);
        return conteudoService.carregar(dietaId).respostas();
    }

    @Transactional
    public RefeicaoResponse criar(UUID nutricionistaId, UUID dietaId, RefeicaoRequest dados) {
        dietaService.editavel(nutricionistaId, dietaId);
        Refeicao refeicao = new Refeicao(dietaId);
        int ordem = dados.ordem() != null ? dados.ordem() : repository.maiorOrdem(dietaId) + 1;
        refeicao.preencher(dados.nome().trim(), dados.horario(), ordem, dados.diasOuTodos(), dados.observacao());
        return resposta(repository.save(refeicao));
    }

    @Transactional
    public RefeicaoResponse atualizar(UUID nutricionistaId, UUID dietaId, UUID id, RefeicaoRequest dados) {
        Refeicao refeicao = editavel(nutricionistaId, dietaId, id);
        int ordem = dados.ordem() != null ? dados.ordem() : refeicao.getOrdem();
        refeicao.preencher(dados.nome().trim(), dados.horario(), ordem, dados.diasOuTodos(), dados.observacao());
        return resposta(repository.saveAndFlush(refeicao));
    }

    @Transactional
    public void excluir(UUID nutricionistaId, UUID dietaId, UUID id) {
        repository.delete(editavel(nutricionistaId, dietaId, id));
    }

    /** Refeição de uma dieta não encerrada do nutricionista. */
    @Transactional(readOnly = true)
    public Refeicao editavel(UUID nutricionistaId, UUID dietaId, UUID id) {
        dietaService.editavel(nutricionistaId, dietaId);
        return repository.findByIdAndDietaId(id, dietaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Refeição não encontrada."));
    }

    private RefeicaoResponse resposta(Refeicao refeicao) {
        return conteudoService.carregar(List.of(refeicao)).resposta(refeicao);
    }
}
