-- =====================================================================
-- ms-nutricionista: limites do plano (réplica da assinatura)
-- =====================================================================

-- Réplica da assinatura (dono: ms-planos), mantida pelo core-assinatura via eventos assinatura.*.
-- Limites efetivos do plano do nutricionista; NULL = ilimitado.
CREATE TABLE assinatura_ref (
  nutricionista_id     uuid PRIMARY KEY,
  assinatura_id        uuid NOT NULL,
  plano_codigo         text NOT NULL,
  status               text NOT NULL,
  limite_pacientes     integer,
  limite_consultorios  integer,
  versao               bigint NOT NULL,
  sincronizado_em      timestamptz NOT NULL DEFAULT now()
);
