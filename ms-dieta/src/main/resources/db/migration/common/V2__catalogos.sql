-- =====================================================================
-- Catálogos do sistema (todos os ambientes).
-- Alimentos: importar a TACO/TBCA numa migração própria (fonte = 'TACO'/'TBCA', nutricionista_id NULL).
-- =====================================================================

-- Mesmos grupos da TACO, para facilitar a importação
INSERT INTO grupo_alimentar (id, nome) VALUES
  (1,  'Cereais e derivados'),
  (2,  'Verduras, hortaliças e derivados'),
  (3,  'Frutas e derivados'),
  (4,  'Gorduras e óleos'),
  (5,  'Pescados e frutos do mar'),
  (6,  'Carnes e derivados'),
  (7,  'Leite e derivados'),
  (8,  'Bebidas'),
  (9,  'Ovos e derivados'),
  (10, 'Produtos açucarados'),
  (11, 'Miscelâneas'),
  (12, 'Outros alimentos industrializados'),
  (13, 'Alimentos preparados'),
  (14, 'Leguminosas e derivados'),
  (15, 'Nozes e sementes');

-- Micronutrientes das colunas da TACO.
-- vd_referencia fica NULL: preencher conforme a IN ANVISA nº 75/2020.
INSERT INTO nutriente (id, nome, unidade) VALUES
  (1,  'Colesterol',  'mg'),
  (2,  'Cálcio',      'mg'),
  (3,  'Magnésio',    'mg'),
  (4,  'Manganês',    'mg'),
  (5,  'Fósforo',     'mg'),
  (6,  'Ferro',       'mg'),
  (7,  'Sódio',       'mg'),
  (8,  'Potássio',    'mg'),
  (9,  'Cobre',       'mg'),
  (10, 'Zinco',       'mg'),
  (11, 'Retinol',     'µg'),
  (12, 'Tiamina',     'mg'),
  (13, 'Riboflavina', 'mg'),
  (14, 'Piridoxina',  'mg'),
  (15, 'Niacina',     'mg'),
  (16, 'Vitamina C',  'mg');
