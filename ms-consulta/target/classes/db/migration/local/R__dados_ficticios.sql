-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Replicas com os mesmos ids do ms-paciente e do ms-nutricionista.
-- =====================================================================

INSERT INTO paciente_ref (id, nutricionista_id, nome, sexo, data_nascimento, status, versao) VALUES
  ('00000000-0000-4000-8000-0000000b0001', '00000000-0000-4000-8000-00000000a001',
   'Carlos Exemplo (ficticio)', 'M', '1995-03-10', 'ATIVO', 0),
  ('00000000-0000-4000-8000-0000000b0002', '00000000-0000-4000-8000-00000000a001',
   'Mariana Exemplo (ficticia)', 'F', '1988-07-22', 'ATIVO', 0),
  ('00000000-0000-4000-8000-0000000b0003', '00000000-0000-4000-8000-00000000a002',
   'Paciente do Bruno (ficticio)', 'M', '1979-01-15', 'ATIVO', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO consultorio_ref (id, nutricionista_id, nome, ativo, versao) VALUES
  ('00000000-0000-4000-8000-0000000c0001', '00000000-0000-4000-8000-00000000a001',
   'Consultorio Centro (ficticio)', true, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO consulta (id, nutricionista_id, paciente_id, consultorio_id, data_hora, duracao_min, tipo,
                      modalidade, status, valor) VALUES
  ('00000000-0000-4000-8000-0000000d0001', '00000000-0000-4000-8000-00000000a001',
   '00000000-0000-4000-8000-0000000b0001', '00000000-0000-4000-8000-0000000c0001',
   '2026-10-08 07:00:00-03', 60, 'RETORNO', 'PRESENCIAL', 'REALIZADA', 250.00),
  ('00000000-0000-4000-8000-0000000d0002', '00000000-0000-4000-8000-00000000a001',
   '00000000-0000-4000-8000-0000000b0002', NULL,
   '2026-10-20 18:30:00-03', 45, 'PRIMEIRA', 'ONLINE', 'AGENDADA', 200.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO analise (id, consulta_id, categoria, descricao) VALUES
  ('00000000-0000-4000-8000-0000000d1001', '00000000-0000-4000-8000-0000000d0001', 'DIAGNOSTICO_NUTRICIONAL',
   'Exemplo ficticio: excesso de gordura corporal com massa muscular preservada.'),
  ('00000000-0000-4000-8000-0000000d1002', '00000000-0000-4000-8000-0000000d0001', 'CONDUTA',
   'Exemplo ficticio: deficit calorico moderado e aumento do consumo de proteina.')
ON CONFLICT (id) DO NOTHING;
