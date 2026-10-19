-- =====================================================================
-- ms-auditoria: trilha de auditoria de todos os serviços (LGPD)
-- =====================================================================

-- Um registro por evento auditoria.*: quem fez o quê, em qual recurso, quando e de onde.
-- Nunca guarda dados pessoais do paciente: só ids, rota e resultado.
CREATE TABLE registro_auditoria (
  id                uuid PRIMARY KEY,                      -- id do evento: reentregas não duplicam
  ocorrido_em       timestamptz NOT NULL,
  recebido_em       timestamptz NOT NULL DEFAULT now(),
  servico           text NOT NULL,
  acao              text NOT NULL,                         -- LEITURA, CRIACAO, ALTERACAO, EXCLUSAO, LOGIN...
  recurso           text,                                  -- raiz do agregado (pacientes, consultas...)
  recurso_id        text,
  paciente_id       uuid,
  nutricionista_id  uuid,                                  -- tenant; NULL em ações sem tenant
  usuario_id        uuid,                                  -- NULL = anônimo ou sistema
  papel             text,
  metodo            text,
  rota              text,                                  -- padrão da rota, sem valores nem query string
  status            integer,
  ip                varchar(45),
  detalhes          jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_registro_nutricionista ON registro_auditoria (nutricionista_id, ocorrido_em DESC);
CREATE INDEX ix_registro_paciente ON registro_auditoria (paciente_id, ocorrido_em DESC) WHERE paciente_id IS NOT NULL;
CREATE INDEX ix_registro_usuario ON registro_auditoria (usuario_id, ocorrido_em DESC) WHERE usuario_id IS NOT NULL;
CREATE INDEX ix_registro_recurso ON registro_auditoria (recurso, recurso_id);
CREATE INDEX ix_registro_ocorrido ON registro_auditoria (ocorrido_em);

-- Somente inserção. Alterar é proibido; apagar, só pela retenção, que liga afitcenter.retencao na transação.
CREATE FUNCTION proteger_registro_auditoria() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' AND current_setting('afitcenter.retencao', true) = 'on' THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'Registros de auditoria não podem ser alterados ou apagados (%).', TG_OP;
END $$;
CREATE TRIGGER trg_registro_auditoria_protegido BEFORE UPDATE OR DELETE ON registro_auditoria
  FOR EACH ROW EXECUTE FUNCTION proteger_registro_auditoria();
CREATE TRIGGER trg_registro_auditoria_sem_truncate BEFORE TRUNCATE ON registro_auditoria
  FOR EACH STATEMENT EXECUTE FUNCTION proteger_registro_auditoria();
