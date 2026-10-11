-- =====================================================================
-- Catálogo do sistema (todos os ambientes). Os nutricionistas podem cadastrar condições próprias.
-- =====================================================================

INSERT INTO tipo_condicao (id, nome) VALUES
  (1, 'Alergia alimentar'),
  (2, 'Intolerância alimentar'),
  (3, 'Doença / condição clínica'),
  (4, 'Restrição física'),
  (5, 'Restrição ética / religiosa'),
  (6, 'Aversão alimentar');

INSERT INTO condicao (tipo_condicao_id, nome) VALUES
  (1, 'Alergia à proteína do leite de vaca (APLV)'),
  (1, 'Alergia a amendoim'),
  (1, 'Alergia a frutos do mar'),
  (1, 'Alergia a ovo'),
  (2, 'Intolerância à lactose'),
  (3, 'Doença celíaca'),
  (3, 'Diabetes mellitus tipo 2'),
  (3, 'Hipertensão arterial'),
  (3, 'Dislipidemia'),
  (3, 'Hipotireoidismo'),
  (3, 'Síndrome dos ovários policísticos'),
  (3, 'Doença renal crônica'),
  (3, 'Gestação'),
  (4, 'Lesão / limitação articular'),
  (4, 'Mobilidade reduzida'),
  (5, 'Vegetarianismo'),
  (5, 'Veganismo');
