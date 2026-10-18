package br.com.afitcenter.pagamentos.provedor;

/** O que o provedor devolve ao criar a cobrança: o id dele e o que o pagador usa para pagar. */
public record DadosPagamento(String idExterno, String pixCopiaECola, String boletoLinhaDigitavel, String urlPagamento) {
}
