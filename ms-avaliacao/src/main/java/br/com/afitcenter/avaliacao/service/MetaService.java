package br.com.afitcenter.avaliacao.service;

import br.com.afitcenter.avaliacao.model.dto.Dtos.MetaRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.MetaResponse;
import br.com.afitcenter.avaliacao.model.entity.Meta;
import br.com.afitcenter.avaliacao.model.entity.Metrica;
import br.com.afitcenter.avaliacao.model.enums.Enums.StatusMeta;
import br.com.afitcenter.avaliacao.repository.MetaRepository;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Metas do paciente. Só métricas do corpo inteiro: a tabela {@code meta} não tem região.
 *
 * <p>As leituras não são {@code readOnly}: {@code exigirDoNutricionista} pode gravar a réplica do
 * paciente quando o evento ainda não chegou (read-through).</p>
 */
@Service
@RequiredArgsConstructor
public class MetaService {

    private final MetaRepository repository;
    private final CatalogoService catalogo;
    private final PacienteRefService pacienteRefService;

    @Transactional
    public List<MetaResponse> listar(UUID nutricionistaId, UUID pacienteId, StatusMeta status) {
        pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);
        List<Meta> metas = status == null
                ? repository.findAllByNutricionistaIdAndPacienteIdOrderByDataInicioDescCriadoEmDesc(nutricionistaId, pacienteId)
                : repository.findAllByNutricionistaIdAndPacienteIdAndStatusOrderByDataInicioDescCriadoEmDesc(
                        nutricionistaId, pacienteId, status);
        return metas.stream().map(MetaResponse::de).toList();
    }

    @Transactional
    public MetaResponse buscar(UUID nutricionistaId, UUID pacienteId, UUID id) {
        return MetaResponse.de(entidade(nutricionistaId, pacienteId, id));
    }

    @Transactional
    public MetaResponse criar(UUID nutricionistaId, UUID pacienteId, MetaRequest dados) {
        pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);
        Meta meta = new Meta(nutricionistaId, pacienteId);
        preencher(meta, dados);
        return MetaResponse.de(repository.save(meta));
    }

    @Transactional
    public MetaResponse atualizar(UUID nutricionistaId, UUID pacienteId, UUID id, MetaRequest dados) {
        Meta meta = entidade(nutricionistaId, pacienteId, id);
        preencher(meta, dados);
        return MetaResponse.de(repository.saveAndFlush(meta));
    }

    /** Exclusão física (meta não é prontuário). Para desistir mantendo o histórico, use o status CANCELADA. */
    @Transactional
    public void excluir(UUID nutricionistaId, UUID pacienteId, UUID id) {
        repository.delete(entidade(nutricionistaId, pacienteId, id));
    }

    /** Metas ativas das métricas pedidas, para a tela de evolução (paciente já validado). */
    @Transactional(readOnly = true)
    public List<MetaResponse> ativas(UUID nutricionistaId, UUID pacienteId, Collection<String> metricas) {
        return repository.porMetricas(nutricionistaId, pacienteId, StatusMeta.ATIVA, metricas).stream()
                .map(MetaResponse::de).toList();
    }

    private Meta entidade(UUID nutricionistaId, UUID pacienteId, UUID id) {
        pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);
        return repository.findByIdAndPacienteIdAndNutricionistaId(id, pacienteId, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Meta não encontrada."));
    }

    private void preencher(Meta meta, MetaRequest dados) {
        Metrica metrica = catalogo.metrica(CatalogoService.normalizar(dados.metricaCodigo()));
        if (metrica.tipoRegiaoExigido() != null)
            throw new RegraNegocioException(metrica.getCodigo() + " é medida por região corporal e não pode ser "
                    + "usada em metas, que valem para o corpo inteiro.");

        LocalDate inicio = dados.dataInicio() != null ? dados.dataInicio()
                : meta.getDataInicio() != null ? meta.getDataInicio() : LocalDate.now();
        if (dados.prazo() != null && dados.prazo().isBefore(inicio))
            throw new RegraNegocioException("O prazo não pode ser anterior à data de início.");

        meta.preencher(metrica, dados.valorAlvo(), inicio, dados.prazo(), dados.status(),
                AvaliacaoService.vazioComoNulo(dados.observacao()));
    }
}
