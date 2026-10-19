package br.com.afitcenter.pagamentos.model.enums;

public final class Enums {

    private Enums() {
    }

    /** O cartão é digitado na página do provedor ({@code urlPagamento}): o número nunca passa pelo AFitCenter. */
    public enum FormaPagamento { PIX, BOLETO, CARTAO_CREDITO }

    /**
     * PENDENTE é a única situação aberta; PAGA ainda pode virar ESTORNADA. Pagamento que chega depois de VENCIDA,
     * CANCELADA ou RECUSADA (Pix pago no último instante, cartão aprovado na segunda análise) é aceito: o dinheiro
     * entrou, e o serviço de origem decide o que fazer.
     */
    public enum StatusCobranca {
        PENDENTE, PAGA, RECUSADA, VENCIDA, CANCELADA, ESTORNADA;

        public boolean aceitaPagamento() {
            return this != PAGA && this != ESTORNADA;
        }
    }
}
