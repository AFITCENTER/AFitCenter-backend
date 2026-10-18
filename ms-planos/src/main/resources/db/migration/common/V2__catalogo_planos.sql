-- =====================================================================
-- Planos iniciais (todos os ambientes). Preços e limites são o ponto de partida comercial e podem ser
-- alterados pelo administrador em /v1/admin/planos sem nova migração.
-- =====================================================================

INSERT INTO plano (codigo, nome, descricao, preco_mensal, preco_anual, limite_pacientes, limite_consultorios,
                   padrao, ordem) VALUES
  ('GRATUITO', 'Gratuito', 'Para começar: todos os recursos, com poucos pacientes.',
   0.00, NULL, 10, 1, true, 10),
  ('PROFISSIONAL', 'Profissional', 'Para o consultório em crescimento.',
   89.90, 899.00, 300, 3, false, 20),
  ('CLINICA', 'Clínica', 'Pacientes e consultórios ilimitados.',
   179.90, 1799.00, NULL, NULL, false, 30);
