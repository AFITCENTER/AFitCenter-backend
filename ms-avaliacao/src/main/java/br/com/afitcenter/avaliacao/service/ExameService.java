package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.integracao.ConsultaVinculoService;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AnexoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AnexoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameResumoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ResultadoExameDto;
import br.com.afitcenter.avaliacao.model.entity.Exame;
import br.com.afitcenter.avaliacao.model.entity.ExameAnexo;
import br.com.afitcenter.avaliacao.model.entity.ExameResultado;
import br.com.afitcenter.avaliacao.model.entity.Metrica;
import br.com.afitcenter.avaliacao.repository.ExameAnexoRepository;
import br.com.afitcenter.avaliacao.repository.ExameRepository;
import br.com.afitcenter.avaliacao.repository.ExameRepository.Filtros;
import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exames laboratoriais (requisito 6): só métricas LABORATORIAL, uma vez cada. Os anexos guardam
 * por enquanto só os metadados; o upload para o storage ainda será implementado.
 */
@Service
@RequiredArgsConstructor
public class ExameService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ExameRepository repository;
    private final ExameAnexoRepository anexoRepository;
    private final CatalogoService catalogo;
    private final PacienteRefService pacienteRefService;
    private final ConsultaVinculoService consultaVinculo;

    @Transactional(readOnly = true)
    public PaginaResponse<ExameResumoResponse> listar(UUID nutricionistaId, UUID pacienteId, LocalDate inicio,
                                                      LocalDate fim, int pagina, int tamanho) {
        if (inicio != null && fim != null && fim.isBefore(inicio))
            throw new RegraNegocioException("O fim do período não pode ser anterior ao início.");

        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.doPaciente(pacienteId))
                .and(Filtros.coletadoDesde(inicio))
                .and(Filtros.coletadoAte(fim));
        Page<Exame> page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA),
                Sort.by(Sort.Direction.DESC, "dataColeta").and(Sort.by(Sort.Direction.DESC, "criadoEm"))));

        Map<UUID, PacienteRef> pacientes = pacienteRefService.buscarTodos(
                page.stream().map(Exame::getPacienteId).collect(Collectors.toSet()));
        return PaginaResponse.de(page, e -> ExameResumoResponse.de(e, AvaliacaoService.nome(pacientes, e.getPacienteId())));
    }

    @Transactional(readOnly = true)
    public ExameResponse buscar(UUID nutricionistaId, UUID id) {
        return resposta(entidade(nutricionistaId, id));
    }

    @Transactional
    public ExameResponse criar(UUID nutricionistaId, ExameRequest dados) {
        pacienteRefService.exigirDoNutricionista(dados.pacienteId(), nutricionistaId);

        Exame exame = new Exame(nutricionistaId, dados.pacienteId());
        preencher(exame, nutricionistaId, dados);
        return resposta(repository.saveAndFlush(exame));
    }

    @Transactional
    public ExameResponse atualizar(UUID nutricionistaId, UUID id, ExameRequest dados) {
        Exame exame = entidade(nutricionistaId, id);
        if (!exame.getPacienteId().equals(dados.pacienteId()))
            throw new RegraNegocioException("Não é possível trocar o paciente de um exame. Registre outro exame.");

        preencher(exame, nutricionistaId, dados);
        return resposta(repository.saveAndFlush(exame));
    }

    /** Exclusão lógica: é prontuário. Resultados e anexos ficam guardados com o exame. */
    @Transactional
    public void excluir(UUID nutricionistaId, UUID id) {
        entidade(nutricionistaId, id).excluir();
    }

    @Transactional
    public AnexoResponse anexar(UUID nutricionistaId, UUID exameId, AnexoRequest dados) {
        entidade(nutricionistaId, exameId);
        return AnexoResponse.de(anexoRepository.save(new ExameAnexo(exameId, dados.storageKey().trim(),
                dados.nomeArquivo().trim(), dados.mimeType().trim(), dados.tamanhoBytes())));
    }

    /** Remove só os metadados; apagar o arquivo no storage virá junto com o upload. */
    @Transactional
    public void removerAnexo(UUID nutricionistaId, UUID exameId, UUID anexoId) {
        entidade(nutricionistaId, exameId);
        anexoRepository.delete(anexoRepository.findByIdAndExameId(anexoId, exameId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado.")));
    }

    private Exame entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Exame não encontrado."));
    }

    /** Validações locais primeiro; a chamada ao ms-consulta (remota) por último. */
    private void preencher(Exame exame, UUID nutricionistaId, ExameRequest dados) {
        List<ExameResultado> resultados = resultados(exame, dados.resultadosOuVazio());
        if (dados.consultaId() != null && !dados.consultaId().equals(exame.getConsultaId()))
            consultaVinculo.exigirDoPaciente(dados.consultaId(), exame.getPacienteId(), nutricionistaId);

        exame.preencher(dados.consultaId(), dados.nome().trim(), AvaliacaoService.vazioComoNulo(dados.laboratorio()),
                dados.dataColeta(), AvaliacaoService.vazioComoNulo(dados.observacao()));
        exame.definirResultados(resultados);
    }

    private List<ExameResultado> resultados(Exame exame, List<ResultadoExameDto> dados) {
        Map<String, Metrica> metricas = catalogo.metricas(dados.stream()
                .map(r -> CatalogoService.normalizar(r.metricaCodigo())).collect(Collectors.toSet()));

        Set<String> vistas = new HashSet<>();
        List<ExameResultado> resultados = new ArrayList<>(dados.size());
        for (ResultadoExameDto r : dados) {
            Metrica metrica = metricas.get(CatalogoService.normalizar(r.metricaCodigo()));
            if (!metrica.isLaboratorial())
                throw new RegraNegocioException(metrica.getCodigo() + " não é uma métrica laboratorial: registre-a "
                        + "em uma avaliação.");
            CatalogoService.validarFaixa(metrica.getCodigo(), r.refMin(), r.refMax());
            if (!vistas.add(metrica.getCodigo()))
                throw new RegraNegocioException("Resultado repetido: " + metrica.getCodigo() + ".");

            resultados.add(new ExameResultado(exame, metrica, r.valor(), r.refMin(), r.refMax(),
                    AvaliacaoService.vazioComoNulo(r.observacao())));
        }
        return resultados;
    }

    private ExameResponse resposta(Exame e) {
        return ExameResponse.de(e,
                AvaliacaoService.nome(pacienteRefService.buscarTodos(Set.of(e.getPacienteId())), e.getPacienteId()),
                anexoRepository.findAllByExameIdOrderByCriadoEm(e.getId()));
    }
}
