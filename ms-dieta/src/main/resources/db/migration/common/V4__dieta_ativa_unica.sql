-- =====================================================================
-- ms-dieta: no máximo uma dieta ATIVA por paciente
-- =====================================================================

-- O serviço já conferia a regra antes de ativar, mas duas ativações simultâneas passavam juntas pela
-- verificação. Se houver pacientes com mais de uma dieta ATIVA, encerre as sobrando antes desta migração.
CREATE UNIQUE INDEX ux_dieta_ativa_por_paciente ON dieta (paciente_id)
  WHERE status = 'ATIVA' AND deletado_em IS NULL;
