package br.com.afitcenter.pagamentos.provedor;

import br.com.afitcenter.pagamentos.model.enums.Enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pedido de criação ao provedor. {@code cobrancaId} vai como referência externa e chave de idempotência do
 * provedor: repetir a chamada depois de um timeout não cria uma segunda cobrança lá.
 */
public record NovaCobrancaProvedor(UUID cobrancaId, String descricao, BigDecimal valor, LocalDate vencimento,
                                   FormaPagamento formaPagamento) {
}
