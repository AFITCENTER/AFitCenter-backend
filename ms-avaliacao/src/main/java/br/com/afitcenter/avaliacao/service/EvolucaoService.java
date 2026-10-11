package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoResumoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ComparacaoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.EvolucaoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ItemComparacao;
import br.com.afitcenter.avaliacao.model.dto.Dtos.PontoEvolucao;
import br.com.afitcenter.avaliacao.model.dto.Dtos.SerieEvolucao;
import br.com.afitcenter.avaliacao.model.entity.Avaliacao;
import br.com.afitcenter.avaliacao.repository.EvolucaoRepository;
import br.com.afitcenter.avaliacao.repository.EvolucaoRepository.Linha;
import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evolução (série para gráficos) e comparação entre duas avaliações (requisito 3), a partir da view
 * {@code vw_evolucao}, que já junta resultados das avaliações, peso/altura/IMC e exames.
 *
 * <p>Não é {@code readOnly}: {@code exigirDoNutricionista} pode gravar a réplica do paciente.</p>
 */
@Service
@RequiredArgsConstructor
public class EvolucaoService {

    private static final int MAXIMO_METRICAS = 20;

    private final EvolucaoRepository repository;
    private final CatalogoService catalogo;
    private final AvaliacaoService avaliacaoService;
    private final MetaService metaService;
    private final PacienteRefService pacienteRefService;

    @Transactional
    public EvolucaoResponse evolucao(UUID nutricionistaId, UUID pacienteId, List<String> metricas, String regiao,
                                     OffsetDateTime inicio, OffsetDateTime fim) {
        pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);

        List<String> codigos = metricas.stream().map(CatalogoService::normalizar).filter(Objects::nonNull)
                .distinct().toList();
        if (codigos.isEmpty())
            throw new RegraNegocioException("Informe ao menos uma métrica.");
        if (codigos.size() > MAXIMO_METRICAS)
            throw new RegraNegocioException("Informe no máximo " + MAXIMO_METRICAS + " métricas.");
        catalogo.metricas(codigos);
        String codigoRegiao = CatalogoService.normalizar(regiao);
        if (codigoRegiao != null)
            catalogo.regioes(List.of(codigoRegiao));
        if (inicio != null && fim != null && !fim.isAfter(inicio))
            throw new RegraNegocioException("O fim do período deve ser depois do início.");

        Map<Posicao, List<Linha>> series = new LinkedHashMap<>();
        for (Linha l : repository.serie(nutricionistaId, pacienteId, codigos, codigoRegiao, inicio, fim))
            series.computeIfAbsent(new Posicao(l.metrica(), l.regiao()), p -> new ArrayList<>()).add(l);

        return new EvolucaoResponse(pacienteId,
                series.values().stream().map(EvolucaoService::serie).toList(),
                metaService.ativas(nutricionistaId, pacienteId, codigos));
    }

    /**
     * Valores das duas avaliações por métrica e região. A ordem dos parâmetros não importa: "antes"
     * é a medição mais antiga.
     */
    @Transactional
    public ComparacaoResponse comparar(UUID nutricionistaId, UUID pacienteId, UUID a, UUID b) {
        PacienteRef paciente = pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);
        if (a.equals(b))
            throw new RegraNegocioException("Escolha duas avaliações diferentes.");

        Avaliacao primeira = doPaciente(nutricionistaId, pacienteId, a);
        Avaliacao segunda = doPaciente(nutricionistaId, pacienteId, b);
        boolean emOrdem = !segunda.getDataHoraMedicao().isBefore(primeira.getDataHoraMedicao());
        Avaliacao antes = emOrdem ? primeira : segunda;
        Avaliacao depois = emOrdem ? segunda : primeira;

        Map<Posicao, Linha[]> pares = new LinkedHashMap<>();
        for (Linha l : repository.daAvaliacoes(nutricionistaId, pacienteId, List.of(a, b)))
            pares.computeIfAbsent(new Posicao(l.metrica(), l.regiao()), p -> new Linha[2])
                    [l.origemId().equals(antes.getId()) ? 0 : 1] = l;

        return new ComparacaoResponse(pacienteId,
                AvaliacaoResumoResponse.de(antes, paciente.nome()),
                AvaliacaoResumoResponse.de(depois, paciente.nome()),
                pares.values().stream().map(par -> item(par[0], par[1])).toList());
    }

    private Avaliacao doPaciente(UUID nutricionistaId, UUID pacienteId, UUID id) {
        Avaliacao avaliacao = avaliacaoService.entidade(nutricionistaId, id);
        if (!avaliacao.getPacienteId().equals(pacienteId))
            throw new RecursoNaoEncontradoException("Avaliação não encontrada.");
        return avaliacao;
    }

    private static SerieEvolucao serie(List<Linha> linhas) {
        Linha primeira = linhas.getFirst();
        return new SerieEvolucao(primeira.metrica(), primeira.metricaNome(), primeira.unidade(), primeira.regiao(),
                linhas.stream().map(l -> new PontoEvolucao(l.dataHora(), l.origem(), l.origemId(), l.equipamentoId(),
                        l.valor(), l.refMin(), l.refMax())).toList());
    }

    private static ItemComparacao item(Linha antes, Linha depois) {
        Linha ref = antes != null ? antes : depois;
        BigDecimal valorAntes = antes == null ? null : antes.valor();
        BigDecimal valorDepois = depois == null ? null : depois.valor();
        BigDecimal diferenca = valorAntes == null || valorDepois == null ? null : valorDepois.subtract(valorAntes);
        return new ItemComparacao(ref.metrica(), ref.metricaNome(), ref.unidade(), ref.regiao(), valorAntes,
                valorDepois, diferenca);
    }

    /** Métrica numa região (nula = corpo inteiro). */
    private record Posicao(String metrica, String regiao) {
    }
}
