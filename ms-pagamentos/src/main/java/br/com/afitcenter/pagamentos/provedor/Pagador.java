package br.com.afitcenter.pagamentos.provedor;

import java.util.UUID;

/**
 * Quem paga a cobrança, como o provedor precisa para emitir Pix e boleto.
 *
 * @param referencia id do pagador no AFitCenter (o nutricionista); provedores que têm cadastro de clientes o usam
 *                   como referência externa, para não criar um cliente por cobrança
 * @param cpfCnpj    só números
 */
public record Pagador(UUID referencia, String nome, String cpfCnpj, String email) {
}
