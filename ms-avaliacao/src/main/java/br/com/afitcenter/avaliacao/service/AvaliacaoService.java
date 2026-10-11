package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.integracao.ConsultaVinculoService;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoResumoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ResultadoAvaliacaoDto;
import br.com.afitcenter.avaliacao.model.entity.Avaliacao;
import br.com.afitcenter.avaliacao.model.entity.AvaliacaoResultado;
import br.com.afitcenter.avaliacao.model.entity.Metrica;
import br.com.afitcenter.avaliacao.model.entity.RegiaoCorporal;
import br.com.afitcenter.avaliacao.repository.AvaliacaoRepository;
import br.com.afitcenter.avaliacao.repository.AvaliacaoRepository.Filtros;
import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avaliações corporais (requisito 3). O paciente vem da réplica local e a FK composta
 * {@code (paciente_id, nutricionista_id)} garante no banco que nada de outro nutricionista entra.
 *
 * <p>Regras dos resultados: métricas BASICA (peso, altura, IMC) são colunas da avaliação;
 * LABORATORIAL só entra em exames; SEGMENTAR e IMPEDANCIA exigem um segmento, PERIMETRO um ponto de
 * perímetro e DOBRA_CUTANEA um ponto de dobra; as demais são do corpo inteiro e não aceitam região.</p>
 */
@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final AvaliacaoRepository repository;
    private final CatalogoService catalogo;
    private final EquipamentoService equipamentoService;
    private final PacienteRefService pacienteRefService;
    private final ConsultaVinculoService consultaVinculo;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PaginaResponse<AvaliacaoResumoResponse> listar(UUID nutricionistaId, UUID pacienteId, OffsetDateTime inicio,
                                                          OffsetDateTime fim, int pagina, int tamanho) {
        if (inicio != null && fim != null && !fim.isAfter(inicio))
            throw new RegraNegocioException("O fim do período deve ser depois do início.");

        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.doPaciente(pacienteId))
                .and(Filtros.aPartirDe(inicio))
                .and(Filtros.antesDe(fim));
        Page<Avaliacao> page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "dataHoraMedicao")));

        Map<UUID, PacienteRef> pacientes = pacienteRefService.buscarTodos(
                page.stream().map(Avaliacao::getPacienteId).collect(Collectors.toSet()));
        return PaginaResponse.de(page, a -> AvaliacaoResumoResponse.de(a, nome(pacientes, a.getPacienteId())));
    }

    @Transactional(readOnly = true)
    public AvaliacaoResponse buscar(UUID nutricionistaId, UUID id) {
        return resposta(entidade(nutricionistaId, id));
    }

    @Transactional
    public AvaliacaoResponse criar(UUID nutricionistaId, AvaliacaoRequest dados) {
        pacienteRefService.exigirDoNutricionista(dados.pacienteId(), nutricionistaId);

        Avaliacao avaliacao = new Avaliacao(nutricionistaId, dados.pacienteId());
        preencher(avaliacao, nutricionistaId, dados);
        return gravar(avaliacao);
    }

    @Transactional
    public AvaliacaoResponse atualizar(UUID nutricionistaId, UUID id, AvaliacaoRequest dados) {
        Avaliacao avaliacao = entidade(nutricionistaId, id);
        if (!avaliacao.getPacienteId().equals(dados.pacienteId()))
            throw new RegraNegocioException("Não é possível trocar o paciente de uma avaliação. Registre outra avaliação.");

        preencher(avaliacao, nutricionistaId, dados);
        return gravar(avaliacao);
    }

    /** Exclusão lógica: é prontuário. A avaliação some das listas e da evolução. */
    @Transactional
    public void excluir(UUID nutricionistaId, UUID id) {
        entidade(nutricionistaId, id).excluir();
    }

    @Transactional(readOnly = true)
    public Avaliacao entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação não encontrada."));
    }

    /** Validações locais primeiro; a chamada ao ms-consulta (remota) por último. */
    private void preencher(Avaliacao avaliacao, UUID nutricionistaId, AvaliacaoRequest dados) {
        List<AvaliacaoResultado> resultados = resultados(avaliacao, dados.resultadosOuVazio());
        if (dados.equipamentoId() != null)
            equipamentoService.exigirVisivel(nutricionistaId, dados.equipamentoId());
        if (dados.consultaId() != null && !dados.consultaId().equals(avaliacao.getConsultaId()))
            consultaVinculo.exigirDoPaciente(dados.consultaId(), avaliacao.getPacienteId(), nutricionistaId);

        avaliacao.preencher(dados.consultaId(), dados.equipamentoId(), dados.metodo(), dados.dataHoraMedicao(),
                dados.pesoKg(), dados.alturaCm(), dados.emJejum(), vazioComoNulo(dados.observacao()),
                vazioComoNulo(dados.laudoUrl()));
        avaliacao.definirResultados(resultados);
    }

    /** Grava e recarrega: o IMC é calculado pelo banco (coluna gerada). */
    private AvaliacaoResponse gravar(Avaliacao avaliacao) {
        repository.saveAndFlush(avaliacao);
        entityManager.refresh(avaliacao);
        return resposta(avaliacao);
    }

    private List<AvaliacaoResultado> resultados(Avaliacao avaliacao, List<ResultadoAvaliacaoDto> dados) {
        Map<String, Metrica> metricas = catalogo.metricas(dados.stream()
                .map(r -> CatalogoService.normalizar(r.metricaCodigo())).collect(Collectors.toSet()));
        Map<String, RegiaoCorporal> regioes = catalogo.regioes(dados.stream()
                .map(r -> CatalogoService.normalizar(r.regiaoCodigo())).filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        Set<String> posicoes = new HashSet<>();
        List<AvaliacaoResultado> resultados = new ArrayList<>(dados.size());
        for (ResultadoAvaliacaoDto r : dados) {
            Metrica metrica = metricas.get(CatalogoService.normalizar(r.metricaCodigo()));
            String codigoRegiao = CatalogoService.normalizar(r.regiaoCodigo());
            RegiaoCorporal regiao = codigoRegiao == null ? null : regioes.get(codigoRegiao);

            validarMetricaERegiao(metrica, regiao);
            CatalogoService.validarFaixa(metrica.getCodigo(), r.refMin(), r.refMax());
            if (!posicoes.add(metrica.getCodigo() + "@" + codigoRegiao))
                throw new RegraNegocioException("Resultado repetido: " + metrica.getCodigo()
                        + (regiao == null ? "" : " em " + regiao.getCodigo()) + ".");

            resultados.add(new AvaliacaoResultado(avaliacao, metrica, regiao, r.valor(), r.refMin(), r.refMax(),
                    vazioComoNulo(r.classificacao())));
        }
        return resultados;
    }

    private static void validarMetricaERegiao(Metrica metrica, RegiaoCorporal regiao) {
        String codigo = metrica.getCodigo();
        if (metrica.isBasica())
            throw new RegraNegocioException(codigo + " não entra nos resultados: peso e altura são campos da "
                    + "avaliação e o IMC é calculado.");
        if (metrica.isLaboratorial())
            throw new RegraNegocioException(codigo + " é uma métrica laboratorial: registre-a em um exame.");

        String tipoExigido = metrica.tipoRegiaoExigido();
        if (tipoExigido == null && regiao != null)
            throw new RegraNegocioException(codigo + " é um valor do corpo inteiro e não aceita região.");
        if (tipoExigido != null && regiao == null)
            throw new RegraNegocioException(codigo + " exige a região corporal.");
        if (tipoExigido != null && !tipoExigido.equals(regiao.getTipo()))
            throw new RegraNegocioException("A região " + regiao.getCodigo() + " não se aplica a " + codigo + ".");
    }

    private AvaliacaoResponse resposta(Avaliacao a) {
        return AvaliacaoResponse.de(a, nome(pacienteRefService.buscarTodos(Set.of(a.getPacienteId())), a.getPacienteId()));
    }

    static String nome(Map<UUID, PacienteRef> refs, UUID id) {
        PacienteRef ref = refs.get(id);
        return ref == null ? null : ref.nome();
    }

    static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
