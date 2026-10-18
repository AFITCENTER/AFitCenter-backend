package br.com.afitcenter.paciente.service;

import br.com.afitcenter.core.assinatura.AssinaturaRefService;
import br.com.afitcenter.core.assinatura.Limite;
import br.com.afitcenter.core.base.validacao.Cpf;
import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.ContatoDto;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteInternoResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteResumoResponse;
import br.com.afitcenter.paciente.model.entity.Paciente;
import br.com.afitcenter.paciente.model.enums.Enums.StatusPaciente;
import br.com.afitcenter.paciente.repository.PacienteRepository;
import br.com.afitcenter.paciente.repository.PacienteRepository.Filtros;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fichas de paciente do nutricionista logado. Toda alteração publica um {@link PacienteEvento}
 * (via outbox, na mesma transação) para as réplicas do ms-consulta, ms-avaliacao e ms-dieta.
 *
 * <p>O número de fichas é limitado pelo plano da assinatura (ms-planos, via core-assinatura).</p>
 */
@Service
@RequiredArgsConstructor
public class PacienteService {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final PacienteRepository repository;
    private final Outbox outbox;
    private final AssinaturaRefService assinaturas;

    @Transactional(readOnly = true)
    public PaginaResponse<PacienteResumoResponse> listar(UUID nutricionistaId, String busca, StatusPaciente status,
                                                         int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId))
                .and(Filtros.comStatus(status))
                .and(Filtros.comBusca(busca));
        var paginacao = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA),
                Sort.by("nome"));
        return PaginaResponse.de(repository.findAll(filtro, paginacao), PacienteResumoResponse::de);
    }

    @Transactional(readOnly = true)
    public PacienteResponse buscar(UUID nutricionistaId, UUID id) {
        return PacienteResponse.de(entidade(nutricionistaId, id));
    }

    @Transactional
    public PacienteResponse criar(UUID nutricionistaId, PacienteRequest dados) {
        String cpf = Cpf.normalizar(dados.cpf());
        if (cpf != null && repository.existsByNutricionistaIdAndCpfAndDeletadoEmIsNull(nutricionistaId, cpf))
            throw new ConflitoException("Você já tem um paciente cadastrado com este CPF.");
        assinaturas.exigirDisponivel(nutricionistaId, Limite.PACIENTES,
                () -> repository.countByNutricionistaIdAndDeletadoEmIsNull(nutricionistaId));

        Paciente paciente = new Paciente(nutricionistaId);
        preencher(paciente, dados, cpf);
        repository.saveAndFlush(paciente);
        publicar(paciente, TipoAlteracao.CRIADO, Eventos.PACIENTE_CRIADO);
        return PacienteResponse.de(paciente);
    }

    @Transactional
    public PacienteResponse atualizar(UUID nutricionistaId, UUID id, PacienteRequest dados) {
        Paciente paciente = entidade(nutricionistaId, id);
        String cpf = Cpf.normalizar(dados.cpf());
        if (cpf != null && repository.existsByNutricionistaIdAndCpfAndDeletadoEmIsNullAndIdNot(nutricionistaId, cpf, id))
            throw new ConflitoException("Você já tem um paciente cadastrado com este CPF.");

        preencher(paciente, dados, cpf);
        repository.saveAndFlush(paciente);
        publicar(paciente, TipoAlteracao.ATUALIZADO, Eventos.PACIENTE_ATUALIZADO);
        return PacienteResponse.de(paciente);
    }

    /** Exclusão lógica: o prontuário é mantido, mas o paciente some das listas e das outras telas. */
    @Transactional
    public void excluir(UUID nutricionistaId, UUID id) {
        Paciente paciente = entidade(nutricionistaId, id);
        paciente.excluir();
        repository.saveAndFlush(paciente);
        publicar(paciente, TipoAlteracao.EXCLUIDO, Eventos.PACIENTE_EXCLUIDO);
    }

    /** Uso interno: inclui pacientes excluídos para que a réplica saiba que foram excluídos. */
    @Transactional(readOnly = true)
    public PacienteInternoResponse buscarInterno(UUID id) {
        return repository.findById(id).map(PacienteInternoResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado."));
    }

    /** Paciente ativo do nutricionista; de outro nutricionista responde 404, como se não existisse. */
    @Transactional(readOnly = true)
    public Paciente entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndDeletadoEmIsNull(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado."));
    }

    private static void preencher(Paciente paciente, PacienteRequest dados, String cpf) {
        List<ContatoDto> contatos = dados.contatos() == null ? List.of() : dados.contatos();
        paciente.preencher(dados.nome().trim(), vazioComoNulo(dados.nomeSocial()), cpf, dados.dataNascimento(),
                dados.sexo(), dados.ocupacao(), dados.objetivo(), dados.nivelAtividade(), dados.observacoes(),
                dados.status(), dados.endereco() == null ? null : dados.endereco().paraEntidade(),
                contatos.stream().map(ContatoDto::paraEntidade).toList());
    }

    private void publicar(Paciente p, TipoAlteracao tipo, String routingKey) {
        outbox.registrar(routingKey, p.getId(), new PacienteEvento(UUID.randomUUID(), tipo, Instant.now(), p.getId(),
                p.getNutricionistaId(), p.nomeExibicao(), p.getSexo().name(), p.getDataNascimento(),
                p.getStatus().name(), p.isExcluido(), p.getVersao()));
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
