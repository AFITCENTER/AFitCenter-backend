package br.com.afitcenter.pagamentos.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.Auditoria;
import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.evento.CobrancaEvento;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CriarCobrancaRequest;
import br.com.afitcenter.pagamentos.model.entity.Cobranca;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import br.com.afitcenter.pagamentos.provedor.DadosPagamento;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento;
import br.com.afitcenter.pagamentos.provedor.NovaCobrancaProvedor;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento.CobrancaJaPagaException;
import br.com.afitcenter.pagamentos.provedor.Provedores;
import br.com.afitcenter.pagamentos.repository.CobrancaRepository;
import br.com.afitcenter.pagamentos.repository.CobrancaRepository.Filtros;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cobranças no provedor de pagamento e os eventos {@code cobranca.*} de cada mudança de situação (via outbox,
 * na mesma transação). As chamadas ao provedor ficam fora das transações de banco.
 *
 * <p>Toda mudança de situação também vai para a trilha de auditoria como ação do sistema.</p>
 */
@Slf4j
@Service
public class CobrancaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int TAMANHO_MAXIMO_PAGINA = 100;
    private static final int LOTE = 100;

    private final CobrancaRepository repository;
    private final Provedores provedores;
    private final Outbox outbox;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final int diasTolerancia;

    public CobrancaService(CobrancaRepository repository, Provedores provedores, Outbox outbox, Auditoria auditoria,
                           PlatformTransactionManager transactionManager, Clock clock,
                           @Value("${afitcenter.pagamentos.dias-tolerancia-vencimento:1}") int diasTolerancia) {
        this.repository = repository;
        this.provedores = provedores;
        this.outbox = outbox;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.diasTolerancia = diasTolerancia;
    }

    // ------------------------------------------------------------------ rotas internas

    /**
     * Cria a cobrança (ou devolve a existente com a mesma chave). Se a criação no provedor falhou antes, repetir
     * o pedido retoma de onde parou.
     */
    public CobrancaResponse criar(CriarCobrancaRequest dados) {
        Cobranca cobranca;
        try {
            cobranca = transacao.execute(status -> registrar(dados));
        } catch (DataIntegrityViolationException e) {
            // Dois pedidos com a mesma chave ao mesmo tempo: o outro gravou primeiro.
            cobranca = repository.findByChaveIdempotencia(dados.chaveIdempotencia()).orElseThrow(() -> e);
        }
        if (cobranca.criadaNoProvedor()) return CobrancaResponse.de(cobranca);

        ProvedorPagamento provedor = provedores.daCobranca(cobranca.getProvedor());
        DadosPagamento pagamento;
        try {
            pagamento = provedor.criar(new NovaCobrancaProvedor(cobranca.getId(), cobranca.getDescricao(),
                    cobranca.getValor(), cobranca.getVencimento(), cobranca.getFormaPagamento()));
        } catch (RuntimeException e) {
            throw new ProvedorIndisponivelException("O provedor de pagamento não criou a cobrança.", e);
        }

        UUID id = cobranca.getId();
        return transacao.execute(status -> {
            Cobranca atual = bloquear(id);
            if (!atual.criadaNoProvedor()) {
                atual.registrarNoProvedor(pagamento);
                repository.saveAndFlush(atual);
                auditoria.registrarSistema(Acoes.CRIACAO, "cobrancas", atual.getId(), atual.getNutricionistaId(),
                        Map.of("origem", atual.getOrigem(), "referenciaId", atual.getReferenciaId().toString(),
                                "formaPagamento", atual.getFormaPagamento().name(), "provedor", atual.getProvedor()));
            }
            return CobrancaResponse.de(atual);
        });
    }

    /** Idempotente para cobranças não pagas; 409 se já foi paga. */
    public void cancelar(UUID id) {
        Cobranca cobranca = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada."));
        exigirNaoPaga(cobranca);
        if (cobranca.getStatus() != StatusCobranca.PENDENTE) return;

        if (cobranca.criadaNoProvedor()) {
            try {
                provedores.daCobranca(cobranca.getProvedor()).cancelar(cobranca.getIdExterno());
            } catch (CobrancaJaPagaException e) {
                throw new ConflitoException("A cobrança já foi paga.");
            } catch (RuntimeException e) {
                throw new ProvedorIndisponivelException("O provedor de pagamento não cancelou a cobrança.", e);
            }
        }
        transacao.executeWithoutResult(status -> {
            Cobranca atual = bloquear(id);
            exigirNaoPaga(atual);
            if (atual.getStatus() == StatusCobranca.PENDENTE)
                encerrar(atual, StatusCobranca.CANCELADA, Eventos.COBRANCA_CANCELADA);
        });
    }

    // ------------------------------------------------------------------ nutricionista e administrador

    @Transactional(readOnly = true)
    public PaginaResponse<CobrancaResponse> listar(UUID nutricionistaId, StatusCobranca status, int pagina, int tamanho) {
        var filtro = Specification.where(Filtros.doNutricionista(nutricionistaId)).and(Filtros.comStatus(status));
        var page = repository.findAll(filtro, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "criadoEm")));
        return PaginaResponse.de(page, CobrancaResponse::de);
    }

    @Transactional(readOnly = true)
    public CobrancaResponse buscar(UUID nutricionistaId, UUID id) {
        return CobrancaResponse.de(doNutricionista(nutricionistaId, id));
    }

    @Transactional(readOnly = true)
    public CobrancaResponse buscarAdmin(UUID id) {
        return CobrancaResponse.de(repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada.")));
    }

    @Transactional(readOnly = true)
    public Cobranca doNutricionista(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaId(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada."));
    }

    /** Devolve o valor pago. O serviço de origem decide o que acontece (ex.: o plano volta ao gratuito). */
    public CobrancaResponse estornar(UUID id) {
        Cobranca cobranca = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada."));
        if (cobranca.getStatus() != StatusCobranca.PAGA)
            throw new RegraNegocioException("Só cobranças pagas podem ser estornadas.");
        try {
            provedores.daCobranca(cobranca.getProvedor()).estornar(cobranca.getIdExterno(), cobranca.getValorPago());
        } catch (RuntimeException e) {
            throw new ProvedorIndisponivelException("O provedor de pagamento não fez o estorno.", e);
        }
        return transacao.execute(status -> {
            Cobranca atual = bloquear(id);
            if (atual.getStatus() == StatusCobranca.PAGA)
                encerrar(atual, StatusCobranca.ESTORNADA, Eventos.COBRANCA_ESTORNADA);
            return CobrancaResponse.de(atual);
        });
    }

    // ------------------------------------------------------------------ provedor e agendador

    /**
     * Aplica uma notificação já autenticada, dentro da transação do webhook.
     *
     * @return o que aconteceu, para o registro da notificação
     */
    String aplicar(Cobranca cobranca, NotificacaoPagamento notificacao) {
        StatusCobranca antes = cobranca.getStatus();
        switch (notificacao.tipo()) {
            case PAGAMENTO_CONFIRMADO -> {
                if (!antes.aceitaPagamento()) return "ignorada: cobrança " + antes;
                if (antes != StatusCobranca.PENDENTE)
                    log.warn("Cobrança {} paga depois de {}: o serviço de origem decide o que fazer.", cobranca.getId(), antes);
                cobranca.pagar(notificacao.ocorridaEm(), notificacao.valorPago());
                if (notificacao.valorPago() != null && notificacao.valorPago().compareTo(cobranca.getValor()) != 0)
                    log.warn("Cobrança {} paga com valor {} diferente do cobrado {}.", cobranca.getId(),
                            notificacao.valorPago(), cobranca.getValor());
                salvarEPublicar(cobranca, Eventos.COBRANCA_PAGA);
            }
            case PAGAMENTO_RECUSADO -> {
                if (antes != StatusCobranca.PENDENTE) return "ignorada: cobrança " + antes;
                encerrar(cobranca, StatusCobranca.RECUSADA, Eventos.COBRANCA_RECUSADA);
            }
            case ESTORNO -> {
                if (antes != StatusCobranca.PAGA) return "ignorada: cobrança " + antes;
                encerrar(cobranca, StatusCobranca.ESTORNADA, Eventos.COBRANCA_ESTORNADA);
            }
            case IGNORADA -> {
                return "ignorada";
            }
        }
        return antes + " -> " + cobranca.getStatus();
    }

    /**
     * Pendentes vencidas há mais de {@code dias-tolerancia-vencimento} (o boleto pode ser pago no dia do
     * vencimento e compensar no dia seguinte) viram VENCIDA.
     *
     * @return quantas venceram
     */
    public int expirar() {
        LocalDate limite = LocalDate.now(clock.withZone(FUSO)).minusDays(diasTolerancia);
        int vencidas = 0;
        for (UUID id : repository.vencidasAntesDe(limite, LOTE)) {
            Cobranca cobranca = repository.findById(id).orElseThrow();
            if (cobranca.criadaNoProvedor()) {
                try {
                    provedores.daCobranca(cobranca.getProvedor()).cancelar(cobranca.getIdExterno());
                } catch (CobrancaJaPagaException e) {
                    continue; // o webhook do pagamento está a caminho
                } catch (RuntimeException e) {
                    log.warn("Não foi possível cancelar no provedor a cobrança vencida {}.", id, e);
                }
            }
            Boolean venceu = transacao.execute(status -> {
                Cobranca atual = bloquear(id);
                if (atual.getStatus() != StatusCobranca.PENDENTE || !atual.getVencimento().isBefore(limite)) return false;
                encerrar(atual, StatusCobranca.VENCIDA, Eventos.COBRANCA_VENCIDA);
                return true;
            });
            if (Boolean.TRUE.equals(venceu)) vencidas++;
        }
        return vencidas;
    }

    Cobranca bloquear(UUID id) {
        return repository.bloquear(id).orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada."));
    }

    // ------------------------------------------------------------------ apoio

    private Cobranca registrar(CriarCobrancaRequest dados) {
        var existente = repository.findByChaveIdempotencia(dados.chaveIdempotencia());
        if (existente.isPresent()) {
            Cobranca c = existente.get();
            boolean mesmoPedido = c.getReferenciaId().equals(dados.referenciaId()) && c.getOrigem().equals(dados.origem())
                    && c.getValor().compareTo(dados.valor()) == 0 && c.getFormaPagamento() == dados.formaPagamento()
                    && Objects.equals(c.getNutricionistaId(), dados.nutricionistaId());
            if (!mesmoPedido)
                throw new ConflitoException("A chave de idempotência já foi usada em outra cobrança.");
            return c;
        }

        // Uma cobrança pendente por referência. As que nunca chegaram ao provedor não foram vistas por ninguém.
        for (Cobranca pendente : repository.findAllByOrigemAndReferenciaIdAndStatus(dados.origem(), dados.referenciaId(),
                StatusCobranca.PENDENTE)) {
            if (pendente.criadaNoProvedor())
                throw new ConflitoException("Já existe uma cobrança pendente para esta referência. Cancele-a antes.");
            pendente.encerrar(StatusCobranca.CANCELADA, clock.instant());
            repository.saveAndFlush(pendente);
        }

        return repository.saveAndFlush(new Cobranca(dados.nutricionistaId(), dados.origem(), dados.referenciaId(),
                dados.chaveIdempotencia(), dados.descricao().trim(), dados.valor(), dados.vencimento(),
                dados.formaPagamento(), provedores.ativo().nome()));
    }

    private static void exigirNaoPaga(Cobranca cobranca) {
        if (cobranca.getStatus() == StatusCobranca.PAGA || cobranca.getStatus() == StatusCobranca.ESTORNADA)
            throw new ConflitoException("A cobrança já foi paga.");
    }

    private void encerrar(Cobranca cobranca, StatusCobranca status, String routingKey) {
        cobranca.encerrar(status, clock.instant());
        salvarEPublicar(cobranca, routingKey);
    }

    private void salvarEPublicar(Cobranca cobranca, String routingKey) {
        repository.saveAndFlush(cobranca);
        outbox.registrar(routingKey, cobranca.getId(), new CobrancaEvento(UUID.randomUUID(), clock.instant(),
                cobranca.getId(), cobranca.getNutricionistaId(), cobranca.getOrigem(), cobranca.getReferenciaId(),
                cobranca.getStatus().name(), cobranca.getValor(), cobranca.getPagoEm(), cobranca.getVersao()));
        auditoria.registrarSistema(Acoes.ALTERACAO, "cobrancas", cobranca.getId(), cobranca.getNutricionistaId(),
                Map.of("status", cobranca.getStatus().name(), "origem", cobranca.getOrigem(),
                        "referenciaId", cobranca.getReferenciaId().toString()));
    }
}
