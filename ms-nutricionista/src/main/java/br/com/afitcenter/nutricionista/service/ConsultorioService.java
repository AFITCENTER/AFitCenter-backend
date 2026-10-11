package br.com.afitcenter.nutricionista.service;

import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.evento.ConsultorioEvento;
import br.com.afitcenter.core.mensageria.evento.TipoAlteracao;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioInternoResponse;
import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioResponse;
import br.com.afitcenter.nutricionista.model.entity.Consultorio;
import br.com.afitcenter.nutricionista.repository.ConsultorioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultórios do nutricionista. Cada alteração publica um evento (via outbox) para o ms-consulta. */
@Service
@RequiredArgsConstructor
public class ConsultorioService {

    private final ConsultorioRepository repository;
    private final PerfilService perfilService;
    private final Outbox outbox;

    @Transactional(readOnly = true)
    public List<ConsultorioResponse> listar(UUID nutricionistaId) {
        return repository.findAllByNutricionistaIdAndAtivoTrueOrderByNome(nutricionistaId).stream()
                .map(ConsultorioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ConsultorioResponse buscar(UUID nutricionistaId, UUID id) {
        return ConsultorioResponse.de(entidade(nutricionistaId, id));
    }

    @Transactional
    public ConsultorioResponse criar(UUID nutricionistaId, ConsultorioRequest dados) {
        perfilService.entidade(nutricionistaId);
        Consultorio consultorio = repository.saveAndFlush(new Consultorio(nutricionistaId, dados.nome().trim(),
                dados.telefone(), dados.endereco() == null ? null : dados.endereco().paraEntidade()));
        publicar(consultorio, TipoAlteracao.CRIADO, Eventos.CONSULTORIO_CRIADO);
        return ConsultorioResponse.de(consultorio);
    }

    @Transactional
    public ConsultorioResponse atualizar(UUID nutricionistaId, UUID id, ConsultorioRequest dados) {
        Consultorio consultorio = entidade(nutricionistaId, id);
        consultorio.atualizar(dados.nome().trim(), dados.telefone(),
                dados.endereco() == null ? null : dados.endereco().paraEntidade());
        repository.saveAndFlush(consultorio);
        publicar(consultorio, TipoAlteracao.ATUALIZADO, Eventos.CONSULTORIO_ATUALIZADO);
        return ConsultorioResponse.de(consultorio);
    }

    /** Exclusão lógica: consultas antigas continuam apontando para o consultório. */
    @Transactional
    public void desativar(UUID nutricionistaId, UUID id) {
        Consultorio consultorio = entidade(nutricionistaId, id);
        consultorio.desativar();
        repository.saveAndFlush(consultorio);
        publicar(consultorio, TipoAlteracao.EXCLUIDO, Eventos.CONSULTORIO_DESATIVADO);
    }

    /** Inclui consultórios desativados: a réplica precisa saber que ele existe, mas está inativo. */
    @Transactional(readOnly = true)
    public ConsultorioInternoResponse buscarInterno(UUID id) {
        return repository.findById(id).map(ConsultorioInternoResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consultório não encontrado."));
    }

    private Consultorio entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaIdAndAtivoTrue(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consultório não encontrado."));
    }

    private void publicar(Consultorio c, TipoAlteracao tipo, String routingKey) {
        outbox.registrar(routingKey, c.getId(), new ConsultorioEvento(UUID.randomUUID(), tipo, Instant.now(),
                c.getId(), c.getNutricionistaId(), c.getNome(), c.isAtivo(), c.getVersao()));
    }
}
