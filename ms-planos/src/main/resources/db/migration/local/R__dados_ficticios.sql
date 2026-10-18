-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Mesmos ids dos nutricionistas do ms-autenticacao/ms-nutricionista e da cobranca do ms-pagamentos.
--   Ana   (a001): Profissional mensal, pago por Pix ha 10 dias (datas relativas a primeira subida)
--   Bruno (a002): Gratuito
-- =====================================================================

INSERT INTO assinatura (id, nutricionista_id, plano_id, ciclo, forma_pagamento, status, periodo_inicio, periodo_fim)
SELECT '00000000-0000-4000-8000-000000090001', '00000000-0000-4000-8000-00000000a001', p.id, 'MENSAL', 'PIX',
       'ATIVA', date_trunc('day', now()) - interval '10 days',
       date_trunc('day', now()) - interval '10 days' + interval '1 month'
FROM plano p WHERE p.codigo = 'PROFISSIONAL'
ON CONFLICT DO NOTHING;

INSERT INTO assinatura (id, nutricionista_id, plano_id)
SELECT '00000000-0000-4000-8000-000000090002', '00000000-0000-4000-8000-00000000a002', p.id
FROM plano p WHERE p.codigo = 'GRATUITO'
ON CONFLICT DO NOTHING;

INSERT INTO fatura (id, assinatura_id, nutricionista_id, tipo, plano_id, ciclo, valor, forma_pagamento, vencimento,
                    periodo_inicio, periodo_fim, status, cobranca_id, paga_em)
SELECT '00000000-0000-4000-8000-000000091001', a.id, a.nutricionista_id, 'MUDANCA_PLANO', a.plano_id, 'MENSAL',
       89.90, 'PIX', (a.periodo_inicio + interval '3 days')::date, a.periodo_inicio, a.periodo_fim, 'PAGA',
       '00000000-0000-4000-8000-000000092001', a.periodo_inicio
FROM assinatura a WHERE a.id = '00000000-0000-4000-8000-000000090001'
ON CONFLICT DO NOTHING;
