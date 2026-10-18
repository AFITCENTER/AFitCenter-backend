-- =====================================================================
-- ms-consulta: outbox para a trilha de auditoria
-- =====================================================================

-- Eventos a publicar no RabbitMQ (core-mensageria): a trilha de auditoria (auditoria.*)
CREATE TABLE outbox_evento (
  id            uuid PRIMARY KEY,
  routing_key   text NOT NULL,
  agregado_id   uuid NOT NULL,
  tipo          text NOT NULL,
  payload       jsonb NOT NULL,
  criado_em     timestamptz NOT NULL DEFAULT now(),
  publicado_em  timestamptz,
  tentativas    integer NOT NULL DEFAULT 0,
  ultimo_erro   text
);
CREATE INDEX ix_outbox_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
