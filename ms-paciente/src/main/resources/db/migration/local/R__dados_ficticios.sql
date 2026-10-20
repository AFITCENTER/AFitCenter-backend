-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Os mesmos ids aparecem nas replicas (paciente_ref) do ms-consulta, ms-avaliacao e ms-dieta.
-- Carlos (b0001) ja tem acesso ao portal: a ficha esta ligada a conta paciente@afitcenter.local
-- (pessoa a101 do ms-pessoa).
-- =====================================================================

INSERT INTO paciente (id, nutricionista_id, nome, cpf, data_nascimento, sexo, ocupacao, objetivo,
                      nivel_atividade, cidade, uf) VALUES
  ('00000000-0000-4000-8000-0000000b0001', '00000000-0000-4000-8000-00000000a001',
   'Carlos Exemplo (ficticio)', '11144477735', '1995-03-10', 'M', 'Analista de sistemas',
   'Reduzir gordura corporal', 'LEVE', 'Sao Paulo', 'SP'),
  ('00000000-0000-4000-8000-0000000b0002', '00000000-0000-4000-8000-00000000a001',
   'Mariana Exemplo (ficticia)', NULL, '1988-07-22', 'F', 'Professora',
   'Ganho de massa muscular', 'MODERADO', 'Sao Paulo', 'SP'),
  ('00000000-0000-4000-8000-0000000b0003', '00000000-0000-4000-8000-00000000a002',
   'Paciente do Bruno (ficticio)', NULL, '1979-01-15', 'M', NULL,
   'Controle glicemico', 'SEDENTARIO', 'Campinas', 'SP')
ON CONFLICT (id) DO NOTHING;

UPDATE paciente SET pessoa_id = '00000000-0000-4000-8000-00000000a101', pessoa_vinculada_em = now()
WHERE id = '00000000-0000-4000-8000-0000000b0001' AND pessoa_id IS NULL;

INSERT INTO paciente_contato (paciente_id, ordem, tipo, valor, principal) VALUES
  ('00000000-0000-4000-8000-0000000b0001', 0, 'WHATSAPP', '11988880001', true),
  ('00000000-0000-4000-8000-0000000b0002', 0, 'CELULAR', '11988880002', true)
ON CONFLICT DO NOTHING;

INSERT INTO paciente_condicao (id, paciente_id, condicao_id, gravidade)
SELECT '00000000-0000-4000-8000-0000000f0001', '00000000-0000-4000-8000-0000000b0001', c.id, 'MODERADA'
FROM condicao c WHERE c.nome = 'Intolerância à lactose' AND c.nutricionista_id IS NULL
ON CONFLICT DO NOTHING;
