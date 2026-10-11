-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local. Nunca em homologacao/producao.
--
-- Nutricionistas de exemplo (os mesmos ids existem no ms-nutricionista):
--   nutri@afitcenter.local   senha: local-nutri-123
--   nutri2@afitcenter.local  senha: local-nutri-123   (outro tenant, para testar isolamento)
-- =====================================================================

INSERT INTO usuario (id, email, senha_hash, perfil) VALUES
  ('00000000-0000-4000-8000-00000000a001', 'nutri@afitcenter.local',
   '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', 'NUTRICIONISTA'),
  ('00000000-0000-4000-8000-00000000a002', 'nutri2@afitcenter.local',
   '$2a$10$gd6AehmcgAhGmIF9ZoRRL.OiUMM/bYowoo0a0cL6T7Qk3zmp67bH2', 'NUTRICIONISTA')
ON CONFLICT (id) DO NOTHING;

INSERT INTO aceite_termo (id, usuario_id, tipo, versao) VALUES
  ('00000000-0000-4000-8000-0000000e0001', '00000000-0000-4000-8000-00000000a001', 'TERMOS_DE_USO', '1.0'),
  ('00000000-0000-4000-8000-0000000e0002', '00000000-0000-4000-8000-00000000a002', 'TERMOS_DE_USO', '1.0')
ON CONFLICT (id) DO NOTHING;
