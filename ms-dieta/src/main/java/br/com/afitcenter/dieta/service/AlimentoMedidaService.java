package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.dieta.model.dto.Dtos.MedidaRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.MedidaResponse;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import br.com.afitcenter.dieta.repository.AlimentoMedidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Medidas caseiras. Qualquer alimento visível pode ser consultado; só os próprios podem ser
 * alterados. Itens de dieta guardam as gramas na inclusão, então mudar a medida não os afeta.
 */
@Service
@RequiredArgsConstructor
public class AlimentoMedidaService {

    /** Usado na verificação de descrição repetida ao criar (nenhuma medida tem este id). */
    private static final Integer NENHUMA = 0;

    private final AlimentoMedidaRepository repository;
    private final AlimentoService alimentoService;

    @Transactional(readOnly = true)
    public List<MedidaResponse> listar(UUID nutricionistaId, Integer alimentoId) {
        alimentoService.visivel(nutricionistaId, alimentoId);
        return repository.findAllByAlimentoIdOrderByGramasAscDescricaoAsc(alimentoId).stream()
                .map(MedidaResponse::de).toList();
    }

    @Transactional
    public MedidaResponse criar(UUID nutricionistaId, Integer alimentoId, MedidaRequest dados) {
        alimentoService.proprio(nutricionistaId, alimentoId);
        String descricao = descricaoUnica(alimentoId, dados.descricao(), NENHUMA);
        return MedidaResponse.de(repository.save(new AlimentoMedida(alimentoId, descricao, dados.gramas())));
    }

    @Transactional
    public MedidaResponse atualizar(UUID nutricionistaId, Integer alimentoId, Integer id, MedidaRequest dados) {
        AlimentoMedida medida = entidade(nutricionistaId, alimentoId, id);
        medida.atualizar(descricaoUnica(alimentoId, dados.descricao(), id), dados.gramas());
        return MedidaResponse.de(repository.saveAndFlush(medida));
    }

    @Transactional
    public void excluir(UUID nutricionistaId, Integer alimentoId, Integer id) {
        AlimentoMedida medida = entidade(nutricionistaId, alimentoId, id);
        if (repository.emUso(id))
            throw new ConflitoException("A medida está em uso em dietas e não pode ser excluída.");
        repository.delete(medida);
    }

    private AlimentoMedida entidade(UUID nutricionistaId, Integer alimentoId, Integer id) {
        alimentoService.proprio(nutricionistaId, alimentoId);
        return repository.findByIdAndAlimentoId(id, alimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medida não encontrada."));
    }

    private String descricaoUnica(Integer alimentoId, String descricao, Integer ignorarId) {
        String limpa = descricao.trim();
        if (repository.existsByAlimentoIdAndDescricaoIgnoreCaseAndIdNot(alimentoId, limpa, ignorarId))
            throw new ConflitoException("O alimento já tem uma medida com essa descrição.");
        return limpa;
    }
}
