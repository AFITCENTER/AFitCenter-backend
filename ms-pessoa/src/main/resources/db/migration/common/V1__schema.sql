-- =====================================================================
-- ms-pessoa: quem acessa o sistema (nutricionistas, pacientes e administradores),
-- credenciais, papéis, aceite de termos e links enviados por e-mail
-- =====================================================================

CREATE FUNCTION set_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.atualizado_em := now();
  RETURN NEW;
END $$;

-- id = sub do JWT. Para o nutricionista, também é o id do perfil no ms-nutricionista e o tenant de todos
-- os dados dele; para o paciente, é o pessoa_id das fichas ligadas à conta no ms-paciente.
CREATE TABLE pessoa (
  id                   uuid PRIMARY KEY,
  nome                 text NOT NULL,
  cpf                  varchar(11) CHECK (cpf ~ '^[0-9]{11}$'),        -- opcional para paciente; o cadastro do nutricionista exige
  email                text NOT NULL CHECK (email = lower(btrim(email))),  -- login, gravado normalizado
  email_confirmado_em  timestamptz,                                     -- quando abriu um link enviado ao e-mail; o login não exige
  telefone             varchar(20),
  senha_hash           text NOT NULL,                                     -- bcrypt, nunca a senha
  status               text NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO', 'BLOQUEADO', 'INATIVO')),
  ultimo_acesso        timestamptz,
  criado_em            timestamptz NOT NULL DEFAULT now(),
  atualizado_em        timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_pessoa_email ON pessoa (email);
CREATE UNIQUE INDEX ux_pessoa_cpf ON pessoa (cpf) WHERE cpf IS NOT NULL;   -- uma conta por pessoa
CREATE TRIGGER trg_pessoa_atualizado_em BEFORE UPDATE ON pessoa
  FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- Uma pessoa pode ter mais de um papel (a nutricionista que também é paciente de um colega).
-- ADMIN só entra por script, nunca por API.
CREATE TABLE pessoa_papel (
  pessoa_id  uuid NOT NULL REFERENCES pessoa (id) ON DELETE CASCADE,
  papel      text NOT NULL CHECK (papel IN ('NUTRICIONISTA', 'PACIENTE', 'ADMIN')),
  PRIMARY KEY (pessoa_id, papel)
);

-- LGPD: qual versão dos termos foi aceita, quando e de onde
CREATE TABLE aceite_termo (
  id          uuid PRIMARY KEY,
  pessoa_id   uuid NOT NULL REFERENCES pessoa (id) ON DELETE CASCADE,
  tipo        text NOT NULL CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_PRIVACIDADE')),
  versao      text NOT NULL,
  aceito_em   timestamptz NOT NULL DEFAULT now(),
  ip          varchar(45),
  UNIQUE (pessoa_id, tipo, versao)
);

-- Links enviados por e-mail: redefinição de senha (vale 30 min) e confirmação do e-mail (48 h). Uso único: usar
-- um link apaga os da mesma finalidade, e pedir outro substitui o anterior. Só o hash é guardado: quem lê o banco
-- não consegue usar o link.
CREATE TABLE token_email (
  id          uuid PRIMARY KEY,
  pessoa_id   uuid NOT NULL REFERENCES pessoa (id) ON DELETE CASCADE,
  finalidade  text NOT NULL CHECK (finalidade IN ('REDEFINICAO_SENHA', 'CONFIRMACAO_EMAIL')),
  token_hash  text NOT NULL UNIQUE,                                   -- SHA-256 do token, nunca o token
  expira_em   timestamptz NOT NULL,
  criado_em   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_token_email_pessoa ON token_email (pessoa_id, finalidade);
