package br.com.afitcenter.dieta.service;

import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.dieta.integracao.ConsultaInternoClient;
import br.com.afitcenter.dieta.integracao.ConsultaInternoClient.ConsultaRef;
import br.com.afitcenter.dieta.model.dto.Dtos.AplicarModeloRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaDetalheResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaResponse;
import br.com.afitcenter.dieta.model.entity.Dieta;
import br.com.afitcenter.dieta.model.enums.Enums.StatusDieta;
import br.com.afitcenter.dieta.repository.DietaRepository;
import br.com.afitcenter.dieta.repository.DietaRepository.Filtros;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dietas de paciente e modelos. O paciente é validado pela réplica local e a FK composta
 * {@code (paciente_id, nutricionista_id)} garante no banco que a dieta não mistura nutricionistas.
 * A consulta, quando informada, é validada no ms-consulta. Exclusão lógica.
 */
@Service
@RequiredArgsConstructor
public class DietaService {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;
    private static final String INDICE_DIETA_ATIVA = "ux_dieta_ativa_por_paciente";
    private static final String JA_TEM_ATIVA = "O paciente já tem uma dieta ativa. Encerre-a ou crie uma nova versão dela.";

    private final DietaRepository repository;
    private final PacienteRefService pacienteRefService;
    private final ConsultaInternoClient consultaClient;
    private final ConteudoDietaService conteudoService;

    @Transactional(readOnly = true)
    public PaginaResponse<DietaResponse> listar(UUID nutricionistaId, UUID pacienteId, StatusDieta status,
                                                int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.modelos(false))
                .and(Filtros.doPaciente(pacienteId))
                .and(Filtros.comStatus(status));
        Page<Dieta> page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "criadoEm")));

        Set<UUID> pacientes = page.stream().map(Dieta::getPacienteId).collect(Collectors.toSet());
        Map<UUID, PacienteRef> refs = pacienteRefService.buscarTodos(pacientes);
        return PaginaResponse.de(page, d -> DietaResponse.de(d, nomePaciente(refs, d.getPacienteId())));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<DietaResponse> listarModelos(UUID nutricionistaId, String busca, int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.modelos(true))
                .and(Filtros.comTitulo(busca));
        Page<Dieta> page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by("titulo")));
        return PaginaResponse.de(page, d -> DietaResponse.de(d, null));
    }

    @Transactional(readOnly = true)
    public DietaDetalheResponse buscar(UUID nutricionistaId, UUID id) {
        return detalhe(entidade(nutricionistaId, id));
    }

    @Transactional
    public DietaResponse criar(UUID nutricionistaId, DietaRequest dados) {
        Dieta dieta;
        if (dados.modelo()) {
            if (dados.pacienteId() != null)
                throw new RegraNegocioException("Modelo não tem paciente. Para usá-lo, aplique o modelo ao paciente.");
            dieta = Dieta.modelo(nutricionistaId);
        } else {
            if (dados.pacienteId() == null)
                throw new RegraNegocioException("Informe o paciente ou marque a dieta como modelo.");
            exigirPacienteAtivo(nutricionistaId, dados.pacienteId());
            dieta = Dieta.doPaciente(nutricionistaId, dados.pacienteId());
        }
        preencher(dieta, nutricionistaId, dados);
        return resposta(repository.save(dieta));
    }

    @Transactional
    public DietaResponse atualizar(UUID nutricionistaId, UUID id, DietaRequest dados) {
        Dieta dieta = editavel(nutricionistaId, id);
        if (dados.modelo() != dieta.isModelo() || !Objects.equals(dados.pacienteId(), dieta.getPacienteId()))
            throw new RegraNegocioException(
                    "Não é possível trocar o paciente da dieta nem transformá-la em modelo. Crie outra dieta.");

        preencher(dieta, nutricionistaId, dados);
        return resposta(repository.saveAndFlush(dieta));
    }

    /** Só uma dieta ATIVA por paciente: para substituí-la, use a nova versão (encerra a anterior). */
    @Transactional
    public DietaResponse alterarStatus(UUID nutricionistaId, UUID id, StatusDieta status) {
        Dieta dieta = entidade(nutricionistaId, id);
        if (status == StatusDieta.ATIVA && dieta.getStatus() != StatusDieta.ATIVA && !dieta.isModelo()
                && repository.existsByPacienteIdAndStatusAndDeletadoEmIsNullAndIdNot(dieta.getPacienteId(),
                StatusDieta.ATIVA, id))
            throw new ConflitoException(JA_TEM_ATIVA);

        dieta.alterarStatus(status);
        return resposta(gravarStatus(dieta));
    }

    @Transactional
    public void excluir(UUID nutricionistaId, UUID id) {
        entidade(nutricionistaId, id).excluir();
    }

    /**
     * Cópia profunda ligada à anterior por {@code dieta_origem_id}. Se a anterior estava ATIVA, ela passa a
     * ENCERRADA e a nova já nasce ATIVA (o paciente não fica sem dieta); senão a nova nasce RASCUNHO.
     * Datas e consulta não são copiadas: a nova versão é de outro período.
     */
    @Transactional
    public DietaDetalheResponse novaVersao(UUID nutricionistaId, UUID id) {
        Dieta anterior = entidade(nutricionistaId, id);
        if (anterior.isModelo())
            throw new RegraNegocioException("Modelos são editados diretamente; versões existem só nas dietas de paciente.");
        exigirPacienteAtivo(nutricionistaId, anterior.getPacienteId());

        Dieta nova = Dieta.copiaPara(anterior, anterior.getPacienteId(), null);
        if (anterior.getStatus() == StatusDieta.ATIVA) {
            anterior.alterarStatus(StatusDieta.ENCERRADA);
            nova.alterarStatus(StatusDieta.ATIVA);
        }
        repository.saveAndFlush(anterior);
        gravarStatus(nova);
        conteudoService.copiar(anterior.getId(), nova.getId());
        return detalhe(nova);
    }

    /** Cópia profunda do modelo para o paciente, em RASCUNHO, com {@code dieta_origem_id} = modelo. */
    @Transactional
    public DietaDetalheResponse aplicarModelo(UUID nutricionistaId, UUID modeloId, AplicarModeloRequest dados) {
        Dieta modelo = repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(modeloId, nutricionistaId)
                .filter(Dieta::isModelo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo não encontrado."));
        exigirPacienteAtivo(nutricionistaId, dados.pacienteId());
        if (dados.consultaId() != null)
            validarConsulta(nutricionistaId, dados.pacienteId(), dados.consultaId());

        Dieta dieta = repository.save(Dieta.copiaPara(modelo, dados.pacienteId(), dados.consultaId()));
        conteudoService.copiar(modelo.getId(), dieta.getId());
        return detalhe(dieta);
    }

    /** Dieta ou modelo do nutricionista; de outro nutricionista (ou excluída) responde 404. */
    @Transactional(readOnly = true)
    public Dieta entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dieta não encontrada."));
    }

    /** Como {@link #entidade}, mas recusa dieta encerrada (refeições e itens também ficam congelados). */
    @Transactional(readOnly = true)
    public Dieta editavel(UUID nutricionistaId, UUID id) {
        Dieta dieta = entidade(nutricionistaId, id);
        dieta.exigirEditavel();
        return dieta;
    }

    /**
     * Grava a dieta que pode ter virado ATIVA. A verificação acima não basta quando duas ativações chegam juntas: o
     * índice único do banco recusa a segunda.
     */
    private Dieta gravarStatus(Dieta dieta) {
        try {
            return repository.saveAndFlush(dieta);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_DIETA_ATIVA))
                throw new ConflitoException(JA_TEM_ATIVA);
            throw e;
        }
    }

    private void preencher(Dieta dieta, UUID nutricionistaId, DietaRequest dados) {
        if (dieta.isModelo() && dados.consultaId() != null)
            throw new RegraNegocioException("Modelos não são vinculados a consultas.");
        if (dados.consultaId() != null && !dados.consultaId().equals(dieta.getConsultaId()))
            validarConsulta(nutricionistaId, dieta.getPacienteId(), dados.consultaId());

        dieta.preencher(dados.consultaId(), dados.titulo().trim(), dados.objetivo(), dados.kcalMeta(),
                dados.proteinaMetaG(), dados.carboidratoMetaG(), dados.lipidioMetaG(), dados.dataInicio(),
                dados.dataFim(), dados.observacao());
    }

    private void exigirPacienteAtivo(UUID nutricionistaId, UUID pacienteId) {
        PacienteRef paciente = pacienteRefService.exigirDoNutricionista(pacienteId, nutricionistaId);
        if (!"ATIVO".equals(paciente.status()))
            throw new RegraNegocioException("O paciente está inativo. Reative o cadastro antes de montar a dieta.");
    }

    /** Consulta de outro nutricionista responde 404, como se não existisse; de outro paciente, 422. */
    private void validarConsulta(UUID nutricionistaId, UUID pacienteId, UUID consultaId) {
        ConsultaRef consulta;
        try {
            consulta = consultaClient.buscar(consultaId);
        } catch (FeignException.NotFound e) {
            consulta = null;
        }
        if (consulta == null || !nutricionistaId.equals(consulta.nutricionistaId()))
            throw new RecursoNaoEncontradoException("Consulta não encontrada.");
        if (!consulta.pacienteId().equals(pacienteId))
            throw new RegraNegocioException("A consulta informada é de outro paciente.");
    }

    private DietaResponse resposta(Dieta dieta) {
        return DietaResponse.de(dieta, nomePaciente(dieta));
    }

    private DietaDetalheResponse detalhe(Dieta dieta) {
        return new DietaDetalheResponse(resposta(dieta), conteudoService.carregar(dieta.getId()).respostas());
    }

    private String nomePaciente(Dieta dieta) {
        if (dieta.getPacienteId() == null) return null;
        return nomePaciente(pacienteRefService.buscarTodos(Set.of(dieta.getPacienteId())), dieta.getPacienteId());
    }

    private static String nomePaciente(Map<UUID, PacienteRef> refs, UUID id) {
        PacienteRef ref = id == null ? null : refs.get(id);
        return ref == null ? null : ref.nome();
    }
}
