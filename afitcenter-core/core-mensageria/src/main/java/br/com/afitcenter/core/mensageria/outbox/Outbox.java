package br.com.afitcenter.core.mensageria.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registra um evento na tabela {@code outbox_evento}, na MESMA transação da alteração de negócio.
 *
 * <p>Se a transação for desfeita, o evento some junto; se for confirmada, o
 * {@link OutboxPublicador} o envia ao RabbitMQ depois. Assim nenhum consumidor recebe evento de
 * algo que não foi gravado, e nada gravado fica sem evento.</p>
 *
 * <p>Tabela exigida na migração do serviço produtor:</p>
 * <pre>
 * CREATE TABLE outbox_evento (
 *   id           uuid PRIMARY KEY,
 *   routing_key  text NOT NULL,
 *   agregado_id  uuid NOT NULL,
 *   tipo         text NOT NULL,
 *   payload      jsonb NOT NULL,
 *   criado_em    timestamptz NOT NULL DEFAULT now(),
 *   publicado_em timestamptz,
 *   tentativas   integer NOT NULL DEFAULT 0,
 *   ultimo_erro  text
 * );
 * CREATE INDEX ix_outbox_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
 * </pre>
 */
public class Outbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Outbox(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String routingKey, UUID agregadoId, Object evento) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o evento " + evento.getClass().getSimpleName(), e);
        }
        jdbc.update("""
                INSERT INTO outbox_evento (id, routing_key, agregado_id, tipo, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), routingKey, agregadoId, evento.getClass().getSimpleName(), payload);
    }
}
