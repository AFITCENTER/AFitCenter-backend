package br.com.afitcenter.auditoria.service;

import br.com.afitcenter.auditoria.model.dto.Dtos.FiltroRegistros;
import br.com.afitcenter.auditoria.model.dto.Dtos.RegistroResponse;
import br.com.afitcenter.auditoria.model.entity.RegistroAuditoria;
import br.com.afitcenter.auditoria.repository.GravacaoRegistros;
import br.com.afitcenter.auditoria.repository.RegistroRepository;
import br.com.afitcenter.auditoria.repository.RegistroRepository.Filtros;
import br.com.afitcenter.core.mensageria.evento.AuditoriaEvento;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grava os registros que chegam pelo RabbitMQ e responde às consultas.
 *
 * <ul>
 *     <li>O nutricionista vê o que aconteceu no próprio tenant: as próprias ações, as do sistema (renovação,
 *     pagamento) e as do administrador sobre os dados dele. O IP só aparece nos registros feitos por ele.</li>
 *     <li>O administrador vê tudo, e cada consulta dele também entra na trilha.</li>
 * </ul>
 */
@Slf4j
@Service
public class RegistroService {

    public static final String FILA = "ms-auditoria.registros";

    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    private final RegistroRepository repository;
    private final GravacaoRegistros gravacao;
    private final String servico;

    public RegistroService(RegistroRepository repository, GravacaoRegistros gravacao,
                           @Value("${spring.application.name}") String servico) {
        this.repository = repository;
        this.gravacao = gravacao;
        this.servico = servico;
    }

    /** Idempotente: a reentrega de um evento já gravado não faz nada. Evento incompleto vai para a DLQ. */
    @RabbitListener(queues = FILA)
    public void receber(AuditoriaEvento evento) {
        if (evento == null || evento.eventoId() == null || evento.ocorridoEm() == null
                || vazio(evento.servico()) || vazio(evento.acao()))
            throw new IllegalArgumentException("Registro de auditoria incompleto.");
        if (!gravacao.inserir(evento))
            log.debug("Registro de auditoria {} já gravado; reentrega ignorada.", evento.eventoId());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<RegistroResponse> doNutricionista(UUID nutricionistaId, FiltroRegistros filtro, int pagina,
                                                            int tamanho) {
        FiltroRegistros doTenant = new FiltroRegistros(filtro.inicio(), filtro.fim(), nutricionistaId, filtro.pacienteId(),
                filtro.usuarioId(), filtro.servico(), filtro.recurso(), filtro.recursoId(), filtro.acao(), null);
        return PaginaResponse.de(buscar(doTenant, pagina, tamanho),
                r -> RegistroResponse.de(r, nutricionistaId.equals(r.getUsuarioId())));
    }

    @Transactional
    public PaginaResponse<RegistroResponse> doAdministrador(UUID administradorId, String ip, FiltroRegistros filtro,
                                                            int pagina, int tamanho) {
        Page<RegistroAuditoria> page = buscar(filtro, pagina, tamanho);
        gravacao.inserir(new AuditoriaEvento(UUID.randomUUID(), Instant.now(), servico, "LEITURA", "auditoria", null,
                filtro.pacienteId(), filtro.nutricionistaId(), administradorId, "ADMIN", "GET",
                "/v1/admin/auditoria/registros", 200, ip, detalhes(filtro)));
        return PaginaResponse.de(page, RegistroResponse::de);
    }

    private Page<RegistroAuditoria> buscar(FiltroRegistros filtro, int pagina, int tamanho) {
        if (filtro.inicio() != null && filtro.fim() != null && !filtro.fim().isAfter(filtro.inicio()))
            throw new RegraNegocioException("O fim do período deve ser depois do início.");
        return repository.findAll(Filtros.de(filtro), PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "ocorridoEm")));
    }

    /** Os filtros usados pelo administrador, para saber depois o que ele procurou. */
    private static Map<String, String> detalhes(FiltroRegistros f) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        colocar(detalhes, "inicio", f.inicio());
        colocar(detalhes, "fim", f.fim());
        colocar(detalhes, "usuarioId", f.usuarioId());
        colocar(detalhes, "servico", f.servico());
        colocar(detalhes, "recurso", f.recurso());
        colocar(detalhes, "recursoId", f.recursoId());
        colocar(detalhes, "acao", f.acao());
        colocar(detalhes, "ip", f.ip());
        return detalhes;
    }

    private static void colocar(Map<String, String> mapa, String chave, Object valor) {
        if (valor != null) mapa.put(chave, valor.toString());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
