package br.com.afitcenter.pagamentos.provedor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Integração com um provedor de pagamento (Asaas, Mercado Pago, Stripe...). Para plugar um provedor real,
 * implemente esta interface como bean e aponte {@code afitcenter.pagamentos.provedor} para o {@link #nome()}.
 *
 * <p>Todas as chamadas são de rede: o {@code CobrancaService} as faz fora das transações de banco. Falhas
 * devem lançar exceção (a operação é repetida pelo chamador com a mesma chave).</p>
 */
public interface ProvedorPagamento {

    /** Nome em maiúsculas; também é o último segmento da URL do webhook ({@code /v1/webhooks/simulado}). */
    String nome();

    DadosPagamento criar(NovaCobrancaProvedor cobranca);

    /** Idempotente. Se a cobrança já foi paga no provedor, lança {@link CobrancaJaPagaException}. */
    void cancelar(String idExterno);

    /**
     * Devolve o valor pago. Idempotente: estorno já pedido não é pedido de novo.
     *
     * @throws EstornoIndisponivelException se o provedor não estorna esta cobrança pela API
     */
    void estornar(String idExterno, BigDecimal valor);

    /**
     * Confere a autenticidade da notificação (assinatura, token) e a traduz.
     *
     * @throws NotificacaoInvalidaException se não for autêntica ou não puder ser lida
     */
    NotificacaoPagamento interpretar(Map<String, String> headers, String corpo);

    class NotificacaoInvalidaException extends RuntimeException {

        public NotificacaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    class CobrancaJaPagaException extends RuntimeException {

        public CobrancaJaPagaException(String idExterno) {
            super("A cobrança " + idExterno + " já foi paga no provedor.");
        }
    }

    /** A mensagem diz ao administrador como estornar (ex.: pelo painel do provedor). */
    class EstornoIndisponivelException extends RuntimeException {

        public EstornoIndisponivelException(String mensagem) {
            super(mensagem);
        }
    }
}
