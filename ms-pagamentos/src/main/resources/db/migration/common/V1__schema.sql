-- =====================================================================
-- ms-pagamentos: cobranças no provedor de pagamento e notificações recebidas
-- =====================================================================

CREATE FUNCTION set_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

CREATE TABLE cobranca (
  id                      uuid PRIMARY KEY,
  nutricionista_id        uuid NOT NULL,
  origem                  text NOT NULL,                       -- quem pediu (ASSINATURA = fatura do ms-planos)
  referencia_id           uuid NOT NULL,                       -- id na origem (fatura)
  chave_idempotencia      text NOT NULL UNIQUE,                -- mesmo pedido = mesma cobrança
  descricao               text NOT NULL,
  valor                   numeric(10,2) NOT NULL CHECK (valor > 0),
  vencimento              date NOT NULL,
  forma_pagamento         text NOT NULL CHECK (forma_pagamento IN ('PIX', 'BOLETO', 'CARTAO_CREDITO')),
  status                  text NOT NULL DEFAULT 'PENDENTE'
                          CHECK (status IN ('PENDENTE', 'PAGA', 'RECUSADA', 'VENCIDA', 'CANCELADA', 'ESTORNADA')),
  provedor                text NOT NULL,
  id_externo              text,                                -- NULL = ainda não criada no provedor
  pix_copia_e_cola        text,
  boleto_linha_digitavel  text,
  url_pagamento           text,                                -- página do provedor (cartão nunca passa por aqui)
  pago_em                 timestamptz,
  valor_pago              numeric(10,2),
  encerrada_em            timestamptz,
  versao                  bigint NOT NULL DEFAULT 0,           -- bloqueio otimista e ordem dos eventos
  criado_em               timestamptz NOT NULL DEFAULT now(),
  atualizado_em           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (provedor, id_externo)
);
CREATE INDEX ix_cobranca_nutricionista ON cobranca (nutricionista_id, criado_em DESC);
CREATE INDEX ix_cobranca_pendente_vencimento ON cobranca (vencimento) WHERE status = 'PENDENTE';
-- Uma cobrança pendente por referência (a anterior é cancelada antes de emitir outra)
CREATE UNIQUE INDEX ux_cobranca_pendente_referencia ON cobranca (origem, referencia_id) WHERE status = 'PENDENTE';
CREATE TRIGGER trg_cobranca_atualizado_em BEFORE UPDATE ON cobranca
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- Notificações (webhooks) já recebidas: o provedor reenvia até receber 2xx, e a reentrega é ignorada.
-- Sem o corpo original: ele pode trazer dados pessoais do pagador.
CREATE TABLE notificacao_provedor (
  provedor     text NOT NULL,
  id_evento    text NOT NULL,
  tipo         text NOT NULL,
  id_externo   text,
  resultado    text,
  recebida_em  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (provedor, id_evento)
);

-- Eventos a publicar no RabbitMQ (core-mensageria): cobranca.* e a trilha de auditoria
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
