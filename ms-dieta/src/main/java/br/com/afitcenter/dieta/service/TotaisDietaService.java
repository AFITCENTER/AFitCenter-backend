package br.com.afitcenter.dieta.service;

import br.com.afitcenter.dieta.model.dto.Dtos.Macros;
import br.com.afitcenter.dieta.model.dto.Dtos.Metas;
import br.com.afitcenter.dieta.model.dto.Dtos.TotaisResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.TotalDia;
import br.com.afitcenter.dieta.model.dto.Dtos.TotalOpcao;
import br.com.afitcenter.dieta.model.dto.Dtos.TotalRefeicao;
import br.com.afitcenter.dieta.model.entity.Dieta;
import br.com.afitcenter.dieta.model.entity.Refeicao;
import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Energia e macros da dieta: por refeição (a opção 1 é a principal; as outras aparecem à parte) e por
 * dia da semana (soma das refeições servidas no dia), comparados com as metas diárias.
 * Cada item vale macro do alimento × quantidade_g / porcao_base_g.
 */
@Service
@RequiredArgsConstructor
public class TotaisDietaService {

    private static final int OPCAO_PRINCIPAL = 1;

    private final DietaService dietaService;
    private final ConteudoDietaService conteudoService;

    @Transactional(readOnly = true)
    public TotaisResponse totais(UUID nutricionistaId, UUID id) {
        Dieta dieta = dietaService.entidade(nutricionistaId, id);
        ConteudoDieta conteudo = conteudoService.carregar(id);

        Map<Refeicao, Macros> principalPorRefeicao = new LinkedHashMap<>();
        List<TotalRefeicao> refeicoes = new ArrayList<>();
        for (Refeicao refeicao : conteudo.refeicoes()) {
            Map<Integer, Macros> porOpcao = new TreeMap<>();
            for (RefeicaoItem item : conteudo.itens(refeicao.getId()))
                porOpcao.merge(item.getOpcao(), conteudo.macros(item), Macros::mais);
            Macros principal = porOpcao.getOrDefault(OPCAO_PRINCIPAL, Macros.ZERO);
            principalPorRefeicao.put(refeicao, principal);
            refeicoes.add(new TotalRefeicao(refeicao.getId(), refeicao.getNome(), refeicao.getHorario(),
                    refeicao.getDiasSemana(), principal.arredondado(),
                    porOpcao.entrySet().stream().map(e -> new TotalOpcao(e.getKey(), e.getValue().arredondado())).toList()));
        }

        List<TotalDia> dias = IntStream.rangeClosed(1, 7).mapToObj(dia -> {
            Macros total = principalPorRefeicao.entrySet().stream()
                    .filter(e -> e.getKey().servidaNoDia(dia))
                    .map(Map.Entry::getValue)
                    .reduce(Macros.ZERO, Macros::mais);
            return new TotalDia(dia, total.arredondado(),
                    CalculoNutricional.comparar(dieta.getKcalMeta(), total.kcal()),
                    CalculoNutricional.comparar(dieta.getProteinaMetaG(), total.proteinaG()),
                    CalculoNutricional.comparar(dieta.getCarboidratoMetaG(), total.carboidratoG()),
                    CalculoNutricional.comparar(dieta.getLipidioMetaG(), total.lipidioG()));
        }).toList();

        return new TotaisResponse(dieta.getId(), Metas.de(dieta), refeicoes, dias);
    }
}
