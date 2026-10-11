package br.com.afitcenter.dieta.service;

import br.com.afitcenter.dieta.model.entity.Alimento;
import br.com.afitcenter.dieta.model.entity.AlimentoMedida;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;
import br.com.afitcenter.dieta.repository.AlimentoMedidaRepository;
import br.com.afitcenter.dieta.repository.AlimentoRepository;
import br.com.afitcenter.dieta.repository.RefeicaoItemRepository;
import br.com.afitcenter.dieta.repository.RefeicaoItemSubstitutoRepository;
import br.com.afitcenter.dieta.repository.RefeicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Carrega e copia o conteúdo das dietas (refeições → itens → substitutos). Não valida o tenant:
 * quem chama já validou a dieta com o nutricionista.
 */
@Service
@RequiredArgsConstructor
public class ConteudoDietaService {

    private final RefeicaoRepository refeicaoRepository;
    private final RefeicaoItemRepository itemRepository;
    private final RefeicaoItemSubstitutoRepository substitutoRepository;
    private final AlimentoRepository alimentoRepository;
    private final AlimentoMedidaRepository medidaRepository;

    @Transactional(readOnly = true)
    ConteudoDieta carregar(UUID dietaId) {
        return carregar(refeicaoRepository.findAllByDietaIdOrderByOrdemAscHorarioAsc(dietaId));
    }

    /** Conteúdo só das refeições informadas (ex.: a refeição recém-alterada). */
    @Transactional(readOnly = true)
    ConteudoDieta carregar(List<Refeicao> refeicoes) {
        List<RefeicaoItem> itens = refeicoes.isEmpty() ? List.of()
                : itemRepository.findAllByRefeicaoIdInOrderByOpcaoAscOrdemAsc(ids(refeicoes, Refeicao::getId));
        List<RefeicaoItemSubstituto> substitutos = itens.isEmpty() ? List.of()
                : substitutoRepository.findAllByRefeicaoItemIdIn(ids(itens, RefeicaoItem::getId));

        List<Integer> alimentoIds = Stream.concat(itens.stream().map(RefeicaoItem::getAlimentoId),
                substitutos.stream().map(RefeicaoItemSubstituto::getAlimentoId)).distinct().toList();
        List<Integer> medidaIds = Stream.concat(itens.stream().map(RefeicaoItem::getAlimentoMedidaId),
                substitutos.stream().map(RefeicaoItemSubstituto::getAlimentoMedidaId))
                .filter(Objects::nonNull).distinct().toList();

        Map<Integer, Alimento> alimentos = alimentoRepository.findAllById(alimentoIds).stream()
                .collect(Collectors.toMap(Alimento::getId, Function.identity()));
        Map<Integer, AlimentoMedida> medidas = medidaRepository.findAllById(medidaIds).stream()
                .collect(Collectors.toMap(AlimentoMedida::getId, Function.identity()));

        return new ConteudoDieta(refeicoes,
                itens.stream().collect(Collectors.groupingBy(RefeicaoItem::getRefeicaoId, LinkedHashMap::new, Collectors.toList())),
                substitutos.stream().collect(Collectors.groupingBy(RefeicaoItemSubstituto::getRefeicaoItemId)),
                alimentos, medidas);
    }

    /** Cópia profunda (modelo aplicado ou nova versão). Quantidades em gramas são copiadas, não recalculadas. */
    @Transactional
    void copiar(UUID origemId, UUID destinoId) {
        ConteudoDieta origem = carregar(origemId);
        for (Refeicao refeicao : origem.refeicoes()) {
            Refeicao novaRefeicao = refeicaoRepository.save(refeicao.copiarPara(destinoId));
            for (RefeicaoItem item : origem.itens(refeicao.getId())) {
                RefeicaoItem novoItem = itemRepository.save(item.copiarPara(novaRefeicao.getId()));
                substitutoRepository.saveAll(origem.substitutos(item.getId()).stream()
                        .map(s -> s.copiarPara(novoItem.getId())).toList());
            }
        }
    }

    private static <T, I> List<I> ids(List<T> entidades, Function<T, I> id) {
        return entidades.stream().map(id).toList();
    }
}
