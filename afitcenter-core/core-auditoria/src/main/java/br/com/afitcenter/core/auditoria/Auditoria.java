package br.com.afitcenter.core.auditoria;

import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.evento.AuditoriaEvento;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Grava registros de auditoria na outbox. As requisições {@code /v1/**} já são registradas pelo
 * {@link AuditoriaInterceptor}; use {@link #registrarSistema} para o que acontece sem requisição
 * (agendadores, eventos recebidos de outros serviços).
 */
public class Auditoria {

    private final Outbox outbox;
    private final String servico;
    /** Entra na transação corrente: o registro só existe se a alteração de negócio for confirmada. */
    private final TransactionTemplate naTransacaoAtual;
    /** Transação própria: a requisição já terminou (e foi confirmada ou desfeita) quando o interceptor roda. */
    private final TransactionTemplate emTransacaoPropria;

    public Auditoria(Outbox outbox, PlatformTransactionManager transactionManager, String servico) {
        this.outbox = outbox;
        this.servico = servico;
        this.naTransacaoAtual = new TransactionTemplate(transactionManager);
        this.emTransacaoPropria = new TransactionTemplate(transactionManager);
        this.emTransacaoPropria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Ação do próprio serviço, sem usuário (papel {@code SISTEMA}).
     *
     * @param detalhes informações extras, sem dados pessoais
     */
    public void registrarSistema(String acao, String recurso, Object recursoId, UUID nutricionistaId,
                                 Map<String, String> detalhes) {
        AuditoriaEvento evento = new AuditoriaEvento(UUID.randomUUID(), Instant.now(), servico, acao, recurso,
                recursoId == null ? null : recursoId.toString(), null, nutricionistaId, null, Acoes.PAPEL_SISTEMA,
                null, null, null, null, detalhes);
        naTransacaoAtual.executeWithoutResult(status -> gravar(evento));
    }

    void registrarRequisicao(AuditoriaEvento evento) {
        emTransacaoPropria.executeWithoutResult(status -> gravar(evento));
    }

    String servico() {
        return servico;
    }

    /** O id do agregado na outbox é o do próprio registro: não se mistura aos eventos do recurso. */
    private void gravar(AuditoriaEvento evento) {
        outbox.registrar(Eventos.AUDITORIA_REGISTRO, evento.eventoId(), evento);
    }
}
