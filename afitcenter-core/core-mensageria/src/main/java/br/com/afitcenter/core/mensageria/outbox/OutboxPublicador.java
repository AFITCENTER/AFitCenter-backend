package br.com.afitcenter.core.mensageria.outbox;

import br.com.afitcenter.core.mensageria.Eventos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Publica os eventos pendentes da outbox no RabbitMQ.
 *
 * <ul>
 *     <li>{@code FOR UPDATE SKIP LOCKED}: com várias réplicas do serviço, cada uma pega linhas
 *     diferentes, sem publicação duplicada.</li>
 *     <li>Publisher confirms ({@code spring.rabbitmq.publisher-confirm-type=simple}): o evento só é
 *     marcado como publicado depois do ack do broker.</li>
 *     <li>Depois de {@code max-tentativas} falhas o evento para de ser reenviado e fica na tabela
 *     para análise ({@code ultimo_erro}).</li>
 * </ul>
 *
 * <p>A entrega é "pelo menos uma vez": consumidores precisam ser idempotentes.</p>
 */
@Slf4j
public class OutboxPublicador {

    private static final int TAMANHO_LOTE = 50;

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transacao;
    private final int maxTentativas;
    private final long timeoutConfirmacaoMs;

    public OutboxPublicador(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate, TransactionTemplate transacao,
                            int maxTentativas, long timeoutConfirmacaoMs) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.transacao = transacao;
        this.maxTentativas = maxTentativas;
        this.timeoutConfirmacaoMs = timeoutConfirmacaoMs;
    }

    private record Pendente(UUID id, String routingKey, String payload, int tentativas) {
    }

    @Scheduled(fixedDelayString = "${afitcenter.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        transacao.executeWithoutResult(status -> {
            List<Pendente> lote = jdbc.query("""
                            SELECT id, routing_key, payload::text AS payload, tentativas
                            FROM outbox_evento
                            WHERE publicado_em IS NULL AND tentativas < ?
                            ORDER BY criado_em
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """,
                    (rs, i) -> new Pendente(rs.getObject("id", UUID.class), rs.getString("routing_key"),
                            rs.getString("payload"), rs.getInt("tentativas")),
                    maxTentativas, TAMANHO_LOTE);

            for (Pendente pendente : lote) {
                try {
                    publicar(pendente);
                    jdbc.update("UPDATE outbox_evento SET publicado_em = now() WHERE id = ?", pendente.id());
                } catch (Exception e) {
                    jdbc.update("UPDATE outbox_evento SET tentativas = tentativas + 1, ultimo_erro = ? WHERE id = ?",
                            resumir(e), pendente.id());
                    if (pendente.tentativas() + 1 >= maxTentativas)
                        log.error("Evento {} ({}) atingiu {} tentativas e não será mais publicado automaticamente.",
                                pendente.id(), pendente.routingKey(), maxTentativas, e);
                    else
                        log.warn("Falha ao publicar o evento {} ({}); nova tentativa em seguida.",
                                pendente.id(), pendente.routingKey(), e);
                }
            }
        });
    }

    private void publicar(Pendente pendente) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propriedades.setMessageId(pendente.id().toString());
        propriedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message mensagem = new Message(pendente.payload().getBytes(StandardCharsets.UTF_8), propriedades);

        rabbitTemplate.invoke(operacoes -> {
            operacoes.send(Eventos.EXCHANGE, pendente.routingKey(), mensagem);
            operacoes.waitForConfirmsOrDie(timeoutConfirmacaoMs);
            return null;
        });
    }

    private static String resumir(Exception e) {
        String mensagem = e.getClass().getSimpleName() + ": " + e.getMessage();
        return mensagem.length() > 1000 ? mensagem.substring(0, 1000) : mensagem;
    }
}
