-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Mesmos ids das pessoas do ms-pessoa, onde ficam nome, CPF, e-mail e senha
-- (veja o R__dados_ficticios.sql de la).
-- =====================================================================

INSERT INTO nutricionista (id, crn_regiao, crn_numero, especialidades, cidade, uf) VALUES
  ('00000000-0000-4000-8000-00000000a001', 3, '10001', 'Nutricao esportiva; emagrecimento', 'Sao Paulo', 'SP'),
  ('00000000-0000-4000-8000-00000000a002', 3, '10002', 'Nutricao clinica', 'Campinas', 'SP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO consultorio (id, nutricionista_id, nome, telefone, cidade, uf) VALUES
  ('00000000-0000-4000-8000-0000000c0001', '00000000-0000-4000-8000-00000000a001',
   'Consultorio Centro (ficticio)', '1130000001', 'Sao Paulo', 'SP')
ON CONFLICT (id) DO NOTHING;
