-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Mesmos ids dos usuarios do ms-autenticacao (veja o R__dados_ficticios.sql de la).
-- CPFs de teste (digitos verificadores validos, sem dono real conhecido).
-- =====================================================================

INSERT INTO nutricionista (id, nome, cpf, crn_regiao, crn_numero, email, telefone, especialidades, cidade, uf) VALUES
  ('00000000-0000-4000-8000-00000000a001', 'Ana Exemplo (ficticia)', '12345678909', 3, '10001',
   'nutri@afitcenter.local', '11900000001', 'Nutricao esportiva; emagrecimento', 'Sao Paulo', 'SP'),
  ('00000000-0000-4000-8000-00000000a002', 'Bruno Exemplo (ficticio)', '98765432100', 3, '10002',
   'nutri2@afitcenter.local', '11900000002', 'Nutricao clinica', 'Campinas', 'SP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO consultorio (id, nutricionista_id, nome, telefone, cidade, uf) VALUES
  ('00000000-0000-4000-8000-0000000c0001', '00000000-0000-4000-8000-00000000a001',
   'Consultorio Centro (ficticio)', '1130000001', 'Sao Paulo', 'SP')
ON CONFLICT (id) DO NOTHING;
