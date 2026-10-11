-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Replica com os mesmos ids do ms-paciente.
-- =====================================================================

INSERT INTO paciente_ref (id, nutricionista_id, nome, sexo, data_nascimento, status, versao) VALUES
  ('00000000-0000-4000-8000-0000000b0001', '00000000-0000-4000-8000-00000000a001',
   'Carlos Exemplo (ficticio)', 'M', '1995-03-10', 'ATIVO', 0),
  ('00000000-0000-4000-8000-0000000b0002', '00000000-0000-4000-8000-00000000a001',
   'Mariana Exemplo (ficticia)', 'F', '1988-07-22', 'ATIVO', 0),
  ('00000000-0000-4000-8000-0000000b0003', '00000000-0000-4000-8000-00000000a002',
   'Paciente do Bruno (ficticio)', 'M', '1979-01-15', 'ATIVO', 0)
ON CONFLICT (id) DO NOTHING;
