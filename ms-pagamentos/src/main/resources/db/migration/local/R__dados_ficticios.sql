-- =====================================================================
-- Dados FICTICIOS, carregados somente no perfil local.
-- Cobranca paga da fatura de adesao da Ana (mesmos ids e datas do R__dados_ficticios.sql do ms-planos).
-- =====================================================================

INSERT INTO cobranca (id, nutricionista_id, origem, referencia_id, chave_idempotencia, descricao, valor, vencimento,
                      forma_pagamento, status, provedor, id_externo, pix_copia_e_cola, pago_em, valor_pago)
VALUES ('00000000-0000-4000-8000-000000092001', '00000000-0000-4000-8000-00000000a001', 'ASSINATURA',
        '00000000-0000-4000-8000-000000091001',
        'fatura:00000000-0000-4000-8000-000000091001:1',
        'AFitCenter - assinatura do plano Profissional (mensal)', 89.90,
        (date_trunc('day', now()) - interval '7 days')::date, 'PIX', 'PAGA', 'SIMULADO',
        'sim_00000000000040008000000000092001', '00020126SIMULADO', date_trunc('day', now()) - interval '10 days', 89.90)
ON CONFLICT (id) DO NOTHING;
