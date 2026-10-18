package br.com.afitcenter.core.mensageria.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mudança de situação de uma cobrança no ms-pagamentos ({@code cobranca.paga}, {@code cobranca.vencida}...).
 *
 * <p>{@code origem} e {@code referenciaId} dizem a quem a cobrança pertence (ex.: {@code ASSINATURA} e o id
 * da fatura no ms-planos): o ms-pagamentos não conhece planos, só cobra. {@code versao} cresce a cada
 * alteração da cobrança.</p>
 */
public record CobrancaEvento(
        UUID eventoId,
        Instant ocorridoEm,
        UUID cobrancaId,
        UUID nutricionistaId,
        String origem,
        UUID referenciaId,
        String status,
        BigDecimal valor,
        Instant pagoEm,
        long versao
) {
}
