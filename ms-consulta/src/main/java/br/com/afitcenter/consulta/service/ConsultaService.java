package br.com.afitcenter.consulta.service;

import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaInternoResponse;
import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaRequest;
import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaResponse;
import br.com.afitcenter.consulta.model.entity.Consulta;
import br.com.afitcenter.consulta.model.enums.Enums.StatusConsulta;
import br.com.afitcenter.consulta.replica.ConsultorioRef;
import br.com.afitcenter.consulta.replica.ConsultorioRefService;
import br.com.afitcenter.consulta.repository.ConsultaRepository;
import br.com.afitcenter.consulta.repository.ConsultaRepository.Filtros;
import br.com.afitcenter.core.paciente.PacienteRef;
import br.com.afitcenter.core.paciente.PacienteRefService;
import br.com.afitcenter.core.web.erro.ConflitoException;
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

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agenda e consultas. Paciente e consultório são validados pelas réplicas locais; as FKs compostas
 * {@code (paciente_id, nutricionista_id)} e {@code (consultorio_id, nutricionista_id)} garantem no
 * banco que nada de outro nutricionista entra numa consulta.
 *
 * <p>A verificação de conflito de horário roda com a agenda do nutricionista bloqueada até o fim da transação.</p>
 */
@Service
@RequiredArgsConstructor
public class ConsultaService {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;
    /** Usado na verificação de conflito ao criar (nenhuma consulta tem este id). */
    private static final UUID NENHUMA = new UUID(0, 0);

    private final ConsultaRepository repository;
    private final PacienteRefService pacienteRefService;
    private final ConsultorioRefService consultorioRefService;

    @Transactional(readOnly = true)
    public PaginaResponse<ConsultaResponse> agenda(UUID nutricionistaId, OffsetDateTime inicio, OffsetDateTime fim,
                                                   UUID pacienteId, StatusConsulta status, int pagina, int tamanho) {
        if (inicio != null && fim != null && !fim.isAfter(inicio))
            throw new RegraNegocioException("O fim do período deve ser depois do início.");

        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.aPartirDe(inicio))
                .and(Filtros.antesDe(fim))
                .and(Filtros.doPaciente(pacienteId))
                .and(Filtros.comStatus(status));
        Page<Consulta> page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by("dataHora")));

        Set<UUID> pacientes = new HashSet<>();
        Set<UUID> consultorios = new HashSet<>();
        page.forEach(c -> {
            pacientes.add(c.getPacienteId());
            if (c.getConsultorioId() != null) consultorios.add(c.getConsultorioId());
        });
        Map<UUID, PacienteRef> refsPacientes = pacienteRefService.buscarTodos(pacientes);
        Map<UUID, ConsultorioRef> refsConsultorios = consultorioRefService.buscarTodos(consultorios);

        return PaginaResponse.de(page, c -> ConsultaResponse.de(c,
                nomePaciente(refsPacientes, c.getPacienteId()),
                nomeConsultorio(refsConsultorios, c.getConsultorioId())));
    }

    @Transactional(readOnly = true)
    public ConsultaResponse buscar(UUID nutricionistaId, UUID id) {
        return resposta(entidade(nutricionistaId, id));
    }

    @Transactional
    public ConsultaResponse criar(UUID nutricionistaId, ConsultaRequest dados) {
        PacienteRef paciente = pacienteRefService.exigirDoNutricionista(dados.pacienteId(), nutricionistaId);
        if (!"ATIVO".equals(paciente.status()))
            throw new RegraNegocioException("O paciente está inativo. Reative o cadastro antes de agendar.");

        Consulta consulta = new Consulta(nutricionistaId, dados.pacienteId());
        preencher(consulta, nutricionistaId, dados, NENHUMA);
        return resposta(repository.save(consulta));
    }

    @Transactional
    public ConsultaResponse atualizar(UUID nutricionistaId, UUID id, ConsultaRequest dados) {
        Consulta consulta = entidade(nutricionistaId, id);
        if (!consulta.getPacienteId().equals(dados.pacienteId()))
            throw new RegraNegocioException("Não é possível trocar o paciente de uma consulta. Crie outra consulta.");

        preencher(consulta, nutricionistaId, dados, id);
        return resposta(repository.saveAndFlush(consulta));
    }

    @Transactional
    public ConsultaResponse alterarStatus(UUID nutricionistaId, UUID id, StatusConsulta status) {
        Consulta consulta = entidade(nutricionistaId, id);
        if (!consulta.getStatus().ocupaHorario() && status.ocupaHorario())
            verificarConflito(nutricionistaId, id, consulta.getDataHora(), consulta.getDuracaoMin());
        consulta.alterarStatus(status);
        return resposta(repository.saveAndFlush(consulta));
    }

    /** Exclusão lógica (para desmarcar, prefira o status CANCELADA: fica no histórico). */
    @Transactional
    public void excluir(UUID nutricionistaId, UUID id) {
        entidade(nutricionistaId, id).excluir();
    }

    @Transactional(readOnly = true)
    public ConsultaInternoResponse buscarInterno(UUID id) {
        return repository.findByIdAndDeletadoEmIsNull(id).map(ConsultaInternoResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta não encontrada."));
    }

    @Transactional(readOnly = true)
    public Consulta entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta não encontrada."));
    }

    private void preencher(Consulta consulta, UUID nutricionistaId, ConsultaRequest dados, UUID ignorarId) {
        if (dados.consultorioId() != null)
            consultorioRefService.exigirAtivoDoNutricionista(dados.consultorioId(), nutricionistaId);
        if (consulta.getStatus().ocupaHorario())
            verificarConflito(nutricionistaId, ignorarId, dados.dataHora(), dados.duracaoOuPadrao());

        consulta.preencher(dados.consultorioId(), dados.dataHora(), dados.duracaoOuPadrao(), dados.tipo(),
                dados.modalidade(), dados.valor(), dados.observacao());
    }

    /** Com a agenda bloqueada: duas marcações simultâneas no mesmo horário não passam juntas pela verificação. */
    private void verificarConflito(UUID nutricionistaId, UUID ignorarId, OffsetDateTime inicio, int duracaoMin) {
        repository.bloquearAgenda(nutricionistaId);
        if (repository.existeConflito(nutricionistaId, ignorarId, inicio, inicio.plusMinutes(duracaoMin)))
            throw new ConflitoException("Já existe uma consulta nesse horário.");
    }

    private ConsultaResponse resposta(Consulta c) {
        Map<UUID, ConsultorioRef> consultorios = c.getConsultorioId() == null ? Map.of()
                : consultorioRefService.buscarTodos(Set.of(c.getConsultorioId()));
        return ConsultaResponse.de(c,
                nomePaciente(pacienteRefService.buscarTodos(Set.of(c.getPacienteId())), c.getPacienteId()),
                nomeConsultorio(consultorios, c.getConsultorioId()));
    }

    private static String nomePaciente(Map<UUID, PacienteRef> refs, UUID id) {
        PacienteRef ref = refs.get(id);
        return ref == null ? null : ref.nome();
    }

    private static String nomeConsultorio(Map<UUID, ConsultorioRef> refs, UUID id) {
        ConsultorioRef ref = id == null ? null : refs.get(id);
        return ref == null ? null : ref.nome();
    }
}
