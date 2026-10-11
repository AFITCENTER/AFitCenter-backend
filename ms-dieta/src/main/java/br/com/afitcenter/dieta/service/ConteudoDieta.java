package br.com.afitcenter.dieta.service;

import br.com.afitcenter.dieta.model.dto.Dtos.ItemResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.Macros;
import br.com.afitcenter.dieta.model.dto.Dtos.RefeicaoResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.SubstitutoResponse;
import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refeições, itens e substitutos de uma dieta, com os alimentos e medidas que eles usam, carregados
 * de uma vez (sem N+1). Base das respostas, dos totais, dos alertas e das cópias.
 */
record ConteudoDieta(
        List<Refeicao> refeicoes,
        Map<UUID, List<RefeicaoItem>> itensPorRefeicao,
        Map<UUID, List<RefeicaoItemSubstituto>> substitutosPorItem,
        Map<Integer, Alimento> alimentos,
        Map<Integer, AlimentoMedida> medidas
) {

    List<RefeicaoItem> itens(UUID refeicaoId) {
        return itensPorRefeicao.getOrDefault(refeicaoId, List.of());
    }

    List<RefeicaoItemSubstituto> substitutos(UUID itemId) {
        return substitutosPorItem.getOrDefault(itemId, List.of());
    }

    Alimento alimento(Integer id) {
        return alimentos.get(id);
    }

    /** Sem arredondar: use nas somas. */
    Macros macros(RefeicaoItem item) {
        return CalculoNutricional.macros(alimento(item.getAlimentoId()), item.getQuantidadeG());
    }

    List<RefeicaoResponse> respostas() {
        return refeicoes.stream().map(this::resposta).toList();
    }

    RefeicaoResponse resposta(Refeicao refeicao) {
        return RefeicaoResponse.de(refeicao, itens(refeicao.getId()).stream().map(this::resposta).toList());
    }

    ItemResponse resposta(RefeicaoItem item) {
        List<SubstitutoResponse> substitutos = substitutos(item.getId()).stream().map(this::resposta).toList();
        return ItemResponse.de(item, alimento(item.getAlimentoId()), medida(item.getAlimentoMedidaId()),
                macros(item).arredondado(), substitutos);
    }

    SubstitutoResponse resposta(RefeicaoItemSubstituto substituto) {
        Alimento alimento = alimento(substituto.getAlimentoId());
        return SubstitutoResponse.de(substituto, alimento, medida(substituto.getAlimentoMedidaId()),
                CalculoNutricional.macros(alimento, substituto.getQuantidadeG()).arredondado());
    }

    private AlimentoMedida medida(Integer id) {
        return id == null ? null : medidas.get(id);
    }
}
