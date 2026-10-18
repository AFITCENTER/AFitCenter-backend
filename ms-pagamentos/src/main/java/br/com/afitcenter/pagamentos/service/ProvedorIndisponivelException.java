package br.com.afitcenter.pagamentos.service;

/** O provedor de pagamento não respondeu ou recusou a chamada (502). A operação pode ser repetida. */
public class ProvedorIndisponivelException extends RuntimeException {

    public ProvedorIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
