-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local. Nunca em homologacao/producao.
--
-- Todas as contas usam a senha local-nutri-123 e já têm o e-mail confirmado:
--   nutri@afitcenter.local     nutricionista Ana (o mesmo id existe no ms-nutricionista)
--   nutri2@afitcenter.local    nutricionista Bruno (outro tenant, para testar isolamento)
--   paciente@afitcenter.local  paciente Carlos, ligado a ficha dele na Ana (ms-paciente)
--   admin@afitcenter.local     papel ADMIN: planos, cobranças e auditoria
-- CPFs de teste (digitos verificadores validos, sem dono real conhecido).
-- =====================================================================

INSERT INTO pessoa (id, nome, cpf, email, telefone, senha_hash, email_confirmado_em) VALUES
  ('00000000-0000-4000-8000-00000000a001', 'Ana Exemplo (ficticia)', '12345678909', 'nutri@afitcenter.local',
   '11900000001', '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', now()),
  ('00000000-0000-4000-8000-00000000a002', 'Bruno Exemplo (ficticio)', '98765432100', 'nutri2@afitcenter.local',
   '11900000002', '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', now()),
  ('00000000-0000-4000-8000-00000000a101', 'Carlos Exemplo (ficticio)', '11144477735', 'paciente@afitcenter.local',
   '11988880001', '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', now()),
  ('00000000-0000-4000-8000-00000000ad01', 'Administrador (ficticio)', NULL, 'admin@afitcenter.local',
   NULL, '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO pessoa_papel (pessoa_id, papel) VALUES
  ('00000000-0000-4000-8000-00000000a001', 'NUTRICIONISTA'),
  ('00000000-0000-4000-8000-00000000a002', 'NUTRICIONISTA'),
  ('00000000-0000-4000-8000-00000000a101', 'PACIENTE'),
  ('00000000-0000-4000-8000-00000000ad01', 'ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO aceite_termo (id, pessoa_id, tipo, versao) VALUES
  ('00000000-0000-4000-8000-0000000e0001', '00000000-0000-4000-8000-00000000a001', 'TERMOS_DE_USO', '1.0'),
  ('00000000-0000-4000-8000-0000000e0002', '00000000-0000-4000-8000-00000000a002', 'TERMOS_DE_USO', '1.0'),
  ('00000000-0000-4000-8000-0000000e0003', '00000000-0000-4000-8000-00000000a101', 'TERMOS_DE_USO', '1.0')
ON CONFLICT (id) DO NOTHING;
