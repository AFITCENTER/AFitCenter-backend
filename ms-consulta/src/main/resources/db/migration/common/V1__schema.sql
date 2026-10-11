-- =====================================================================
-- ms-consulta: agenda/consultas (requisito 4) e análises do nutricionista (requisito 2)
-- =====================================================================

CREATE FUNCTION set_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

-- Réplica do paciente (dono: ms-paciente), mantida pelo core-paciente via eventos paciente.*
CREATE TABLE paciente_ref (
  id               uuid PRIMARY KEY,
  nutricionista_id uuid NOT NULL,
  nome             text NOT NULL,
  sexo             varchar(1) NOT NULL,
  data_nascimento  date NOT NULL,
  status           text NOT NULL,
  excluido         boolean NOT NULL DEFAULT false,
  versao           bigint NOT NULL,
  sincronizado_em  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, nutricionista_id)
);

-- Réplica do consultório (dono: ms-nutricionista), mantida via eventos consultorio.*
CREATE TABLE consultorio_ref (
  id               uuid PRIMARY KEY,
  nutricionista_id uuid NOT NULL,
  nome             text NOT NULL,
  ativo            boolean NOT NULL,
  versao           bigint NOT NULL,
  sincronizado_em  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, nutricionista_id)
);

CREATE TABLE consulta (
  id                uuid PRIMARY KEY,
  nutricionista_id  uuid NOT NULL,
  paciente_id       uuid NOT NULL,
  consultorio_id    uuid,                                    -- NULL = online / fora de consultório
  data_hora         timestamptz NOT NULL,
  duracao_min       integer NOT NULL DEFAULT 60 CHECK (duracao_min BETWEEN 5 AND 480),
  tipo              text NOT NULL CHECK (tipo IN ('PRIMEIRA', 'RETORNO')),
  modalidade        text NOT NULL CHECK (modalidade IN ('PRESENCIAL', 'ONLINE')),
  status            text NOT NULL DEFAULT 'AGENDADA'
                    CHECK (status IN ('AGENDADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA', 'FALTOU')),
  valor             numeric(10,2) CHECK (valor >= 0),
  observacao        text,
  versao            bigint NOT NULL DEFAULT 0,
  criado_em         timestamptz NOT NULL DEFAULT now(),
  atualizado_em     timestamptz NOT NULL DEFAULT now(),
  deletado_em       timestamptz,
  -- O banco garante que paciente e consultório são do MESMO nutricionista da consulta.
  FOREIGN KEY (paciente_id, nutricionista_id)    REFERENCES paciente_ref (id, nutricionista_id),
  FOREIGN KEY (consultorio_id, nutricionista_id) REFERENCES consultorio_ref (id, nutricionista_id)
);
CREATE INDEX ix_consulta_agenda   ON consulta (nutricionista_id, data_hora) WHERE deletado_em IS NULL;
CREATE INDEX ix_consulta_paciente ON consulta (paciente_id, data_hora) WHERE deletado_em IS NULL;
CREATE TRIGGER trg_consulta_atualizado_em BEFORE UPDATE ON consulta
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

CREATE TABLE analise (
  id             uuid PRIMARY KEY,
  consulta_id    uuid NOT NULL REFERENCES consulta (id),
  categoria      text NOT NULL CHECK (categoria IN
                   ('ANAMNESE', 'QUEIXA', 'ACHADO', 'DIAGNOSTICO_NUTRICIONAL', 'CONDUTA', 'OUTRO')),
  descricao      text NOT NULL,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  atualizado_em  timestamptz NOT NULL DEFAULT now(),
  deletado_em    timestamptz
);
CREATE INDEX ix_analise_consulta ON analise (consulta_id) WHERE deletado_em IS NULL;
CREATE TRIGGER trg_analise_atualizado_em BEFORE UPDATE ON analise
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();
