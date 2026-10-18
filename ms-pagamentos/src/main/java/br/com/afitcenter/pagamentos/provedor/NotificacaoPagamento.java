package br.com.afitcenter.pagamentos.provedor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Notificação do provedor já autenticada e traduzida.
 *
 * @param idEvento  id da notificação no provedor (reentregas trazem o mesmo id)
 * @param idExterno id da cobrança no provedor
 */
public record NotificacaoPagamento(String idEvento, String idExterno, Tipo tipo, Instant ocorridaEm, BigDecimal valorPago) {

    public enum Tipo {
        PAGAMENTO_CONFIRMADO,
        PAGAMENTO_RECUSADO,
        ESTORNO,
        /** Evento que não muda nada aqui (ex.: cobrança visualizada). */
        IGNORADA
    }
}
