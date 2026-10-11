-- =====================================================================
-- ms-autenticacao: credenciais e aceite de termos
-- =====================================================================

CREATE FUNCTION set_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

CREATE TABLE usuario (
  id             uuid PRIMARY KEY,
  email          text NOT NULL CHECK (email = lower(btrim(email))),  -- gravado normalizado
  senha_hash     text NOT NULL,                                     -- bcrypt, nunca a senha
  perfil         text NOT NULL CHECK (perfil IN ('NUTRICIONISTA', 'PACIENTE', 'ADMIN')),
  status         text NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO', 'BLOQUEADO', 'INATIVO')),
  ultimo_acesso  timestamptz,
  criado_em      timestamptz NOT NULL DEFAULT now(),
  atualizado_em  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_usuario_email ON usuario (email);
CREATE TRIGGER trg_usuario_atualizado_em BEFORE UPDATE ON usuario
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- LGPD: qual versão dos termos foi aceita, quando e de onde
CREATE TABLE aceite_termo (
  id          uuid PRIMARY KEY,
  usuario_id  uuid NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
  tipo        text NOT NULL CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_PRIVACIDADE')),
  versao      text NOT NULL,
  aceito_em   timestamptz NOT NULL DEFAULT now(),
  ip          varchar(45),
  UNIQUE (usuario_id, tipo, versao)
);
