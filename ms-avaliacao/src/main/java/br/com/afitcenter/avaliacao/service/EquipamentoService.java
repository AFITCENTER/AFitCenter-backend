package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.model.dto.Dtos.EquipamentoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.EquipamentoResponse;
import br.com.afitcenter.avaliacao.model.entity.Equipamento;
import br.com.afitcenter.avaliacao.repository.AvaliacaoRepository;
import br.com.afitcenter.avaliacao.repository.EquipamentoRepository;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Equipamentos: o nutricionista vê o catálogo do sistema e os próprios, e só altera os próprios.
 * Equipamento de outro nutricionista responde 404.
 */
@Service
@RequiredArgsConstructor
public class EquipamentoService {

    /** Usado na verificação de duplicidade ao criar (ids começam em 1). */
    private static final int NENHUM = 0;

    private final EquipamentoRepository repository;
    private final AvaliacaoRepository avaliacaoRepository;

    @Transactional(readOnly = true)
    public List<EquipamentoResponse> listar(UUID nutricionistaId) {
        return repository.findVisiveis(nutricionistaId).stream().map(EquipamentoResponse::de).toList();
    }

    @Transactional
    public EquipamentoResponse criar(UUID nutricionistaId, EquipamentoRequest dados) {
        Equipamento equipamento = new Equipamento(nutricionistaId);
        preencher(equipamento, nutricionistaId, dados, NENHUM);
        return EquipamentoResponse.de(repository.save(equipamento));
    }

    @Transactional
    public EquipamentoResponse atualizar(UUID nutricionistaId, Integer id, EquipamentoRequest dados) {
        Equipamento equipamento = proprio(nutricionistaId, id);
        preencher(equipamento, nutricionistaId, dados, id);
        return EquipamentoResponse.de(repository.saveAndFlush(equipamento));
    }

    /** Exclusão física: o equipamento é cadastro, não prontuário. Em uso por alguma avaliação, 409. */
    @Transactional
    public void excluir(UUID nutricionistaId, Integer id) {
        Equipamento equipamento = proprio(nutricionistaId, id);
        if (avaliacaoRepository.existsByEquipamentoId(id))
            throw new ConflitoException("O equipamento está registrado em avaliações e não pode ser excluído.");
        repository.delete(equipamento);
    }

    /** Equipamento que o nutricionista pode usar numa avaliação (do sistema ou dele). */
    @Transactional(readOnly = true)
    public Equipamento exigirVisivel(UUID nutricionistaId, Integer id) {
        return repository.findById(id)
                .filter(e -> e.isDoSistema() || e.getNutricionistaId().equals(nutricionistaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento não encontrado."));
    }

    private Equipamento proprio(UUID nutricionistaId, Integer id) {
        Equipamento equipamento = exigirVisivel(nutricionistaId, id);
        if (equipamento.isDoSistema())
            throw new RegraNegocioException("Equipamentos do catálogo do sistema não podem ser alterados nem excluídos.");
        return equipamento;
    }

    private void preencher(Equipamento equipamento, UUID nutricionistaId, EquipamentoRequest dados, int ignorarId) {
        String marca = dados.marca().trim();
        String modelo = dados.modelo().trim();
        if (repository.existeVisivel(nutricionistaId, marca, modelo, ignorarId))
            throw new ConflitoException("Já existe um equipamento " + marca + " " + modelo + ".");
        equipamento.preencher(dados.tipo(), marca, modelo);
    }
}
