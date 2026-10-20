-- =====================================================================
-- ms-nutricionista: perfil profissional e consultórios (requisito 7)
-- =====================================================================

CREATE FUNCTION set_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

-- Perfil profissional. id = pessoa.id no ms-pessoa = tenant de todos os dados do nutricionista.
-- Nome, CPF, e-mail (login) e telefone ficam só no ms-pessoa.
CREATE TABLE nutricionista (
  id              uuid PRIMARY KEY,
  crn_regiao      integer NOT NULL CHECK (crn_regiao > 0),   -- CRN-3 → 3
  crn_numero      varchar(20) NOT NULL,
  especialidades  text,
  assinatura_url  text,                                      -- usada no PDF da dieta
  logo_url        text,
  -- endereço profissional
  cep             varchar(8),
  logradouro      text,
  numero          varchar(20),
  complemento     text,
  bairro          text,
  cidade          text,
  uf              varchar(2),
  criado_em       timestamptz NOT NULL DEFAULT now(),
  atualizado_em   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (crn_regiao, crn_numero)
);
CREATE TRIGGER trg_nutricionista_atualizado_em BEFORE UPDATE ON nutricionista
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

CREATE TABLE consultorio (
  id                uuid PRIMARY KEY,
  nutricionista_id  uuid NOT NULL REFERENCES nutricionista (id),
  nome              text NOT NULL,
  telefone          varchar(20),
  cep               varchar(8),
  logradouro        text,
  numero            varchar(20),
  complemento       text,
  bairro            text,
  cidade            text,
  uf                varchar(2),
  ativo             boolean NOT NULL DEFAULT true,
  versao            bigint NOT NULL DEFAULT 0,               -- bloqueio otimista e ordem dos eventos
  criado_em         timestamptz NOT NULL DEFAULT now(),
  atualizado_em     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_consultorio_nutricionista ON consultorio (nutricionista_id) WHERE ativo;
CREATE TRIGGER trg_consultorio_atualizado_em BEFORE UPDATE ON consultorio
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- Eventos a publicar no RabbitMQ (core-mensageria)
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
