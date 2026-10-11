package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.dieta.model.dto.Dtos.ItemRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.ItemResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.SubstitutoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.SubstitutoResponse;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;
import br.com.afitcenter.dieta.repository.AlimentoMedidaRepository;
import br.com.afitcenter.dieta.repository.RefeicaoItemRepository;
import br.com.afitcenter.dieta.repository.RefeicaoItemSubstitutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Itens das refeições e seus substitutos. Só entram alimentos visíveis ao nutricionista (base oficial
 * ou próprios ativos), e a medida caseira precisa ser do mesmo alimento. {@code quantidade_g} é
 * calculado aqui e gravado.
 */
@Service
@RequiredArgsConstructor
public class RefeicaoItemService {

    private final RefeicaoItemRepository itemRepository;
    private final RefeicaoItemSubstitutoRepository substitutoRepository;
    private final AlimentoMedidaRepository medidaRepository;
    private final RefeicaoService refeicaoService;
    private final AlimentoService alimentoService;
    private final ConteudoDietaService conteudoService;

    @Transactional
    public ItemResponse adicionarItem(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, ItemRequest dados) {
        Refeicao refeicao = refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        alimentoService.visivel(nutricionistaId, dados.alimentoId());

        RefeicaoItem item = new RefeicaoItem(refeicaoId);
        int ordem = dados.ordem() != null ? dados.ordem() : itemRepository.maiorOrdem(refeicaoId) + 1;
        item.preencher(dados.alimentoId(), dados.opcaoOuPrincipal(), dados.quantidade(), dados.alimentoMedidaId(),
                gramas(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId()), ordem, dados.observacao());
        return resposta(refeicao, itemRepository.save(item));
    }

    @Transactional
    public ItemResponse atualizarItem(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, UUID id, ItemRequest dados) {
        Refeicao refeicao = refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        RefeicaoItem item = item(refeicaoId, id);
        // O alimento atual pode ter sido desativado depois: só revalida quando muda.
        if (!dados.alimentoId().equals(item.getAlimentoId()))
            alimentoService.visivel(nutricionistaId, dados.alimentoId());

        int ordem = dados.ordem() != null ? dados.ordem() : item.getOrdem();
        item.preencher(dados.alimentoId(), dados.opcaoOuPrincipal(), dados.quantidade(), dados.alimentoMedidaId(),
                gramas(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId()), ordem, dados.observacao());
        return resposta(refeicao, itemRepository.saveAndFlush(item));
    }

    /** Exclusão física; os substitutos vão junto (ON DELETE CASCADE). */
    @Transactional
    public void excluirItem(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, UUID id) {
        refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        itemRepository.delete(item(refeicaoId, id));
    }

    @Transactional
    public SubstitutoResponse adicionarSubstituto(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, UUID itemId,
                                                  SubstitutoRequest dados) {
        Refeicao refeicao = refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        item(refeicaoId, itemId);
        alimentoService.visivel(nutricionistaId, dados.alimentoId());

        RefeicaoItemSubstituto substituto = new RefeicaoItemSubstituto(itemId);
        substituto.preencher(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId(),
                gramas(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId()));
        return resposta(refeicao, substitutoRepository.save(substituto));
    }

    @Transactional
    public SubstitutoResponse atualizarSubstituto(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, UUID itemId,
                                                  UUID id, SubstitutoRequest dados) {
        Refeicao refeicao = refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        item(refeicaoId, itemId);
        RefeicaoItemSubstituto substituto = substituto(itemId, id);
        if (!dados.alimentoId().equals(substituto.getAlimentoId()))
            alimentoService.visivel(nutricionistaId, dados.alimentoId());

        substituto.preencher(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId(),
                gramas(dados.alimentoId(), dados.quantidade(), dados.alimentoMedidaId()));
        return resposta(refeicao, substitutoRepository.saveAndFlush(substituto));
    }

    @Transactional
    public void excluirSubstituto(UUID nutricionistaId, UUID dietaId, UUID refeicaoId, UUID itemId, UUID id) {
        refeicaoService.editavel(nutricionistaId, dietaId, refeicaoId);
        item(refeicaoId, itemId);
        substitutoRepository.delete(substituto(itemId, id));
    }

    private RefeicaoItem item(UUID refeicaoId, UUID id) {
        return itemRepository.findByIdAndRefeicaoId(id, refeicaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado."));
    }

    private RefeicaoItemSubstituto substituto(UUID itemId, UUID id) {
        return substitutoRepository.findByIdAndRefeicaoItemId(id, itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Substituto não encontrado."));
    }

    /** Gramas da medida × quantidade; sem medida, a quantidade já está em gramas. */
    private BigDecimal gramas(Integer alimentoId, BigDecimal quantidade, Integer medidaId) {
        AlimentoMedida medida = medidaId == null ? null : medidaRepository.findByIdAndAlimentoId(medidaId, alimentoId)
                .orElseThrow(() -> new RegraNegocioException("A medida caseira informada não é deste alimento."));
        return CalculoNutricional.gramas(quantidade, medida);
    }

    private ItemResponse resposta(Refeicao refeicao, RefeicaoItem item) {
        return conteudoService.carregar(List.of(refeicao)).resposta(item);
    }

    private SubstitutoResponse resposta(Refeicao refeicao, RefeicaoItemSubstituto substituto) {
        return conteudoService.carregar(List.of(refeicao)).resposta(substituto);
    }
}
