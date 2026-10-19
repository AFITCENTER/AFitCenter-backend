package br.com.afitcenter.pagamentos.model.dto;

import br.com.afitcenter.pagamentos.model.entity.Cobranca;
import br.com.afitcenter.pagamentos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos HTTP do ms-pagamentos. */
public final class Dtos {

    private Dtos() {
    }

    /**
     * {@code POST /internal/cobrancas}. A mesma {@code chaveIdempotencia} devolve sempre a mesma cobrança: o
     * serviço de origem pode repetir o pedido depois de um timeout sem cobrar duas vezes.
     */
    public record CriarCobrancaRequest(
            @NotBlank @Size(max = 200) String chaveIdempotencia,
            @NotNull UUID nutricionistaId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String origem,
            @NotNull UUID referenciaId,
            @NotBlank @Size(max = 200) String descricao,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal valor,
            @NotNull LocalDate vencimento,
            @NotNull FormaPagamento formaPagamento,
            @NotNull @Valid PagadorRequest pagador
    ) {
    }

    /**
     * Quem paga (o nutricionista, na assinatura). Vai só para o provedor, que exige nome e CPF/CNPJ para emitir
     * Pix e boleto; o ms-pagamentos não guarda estes dados.
     */
    public record PagadorRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Pattern(regexp = "\\d{11}|\\d{14}", message = "Informe o CPF ou CNPJ só com números.") String cpfCnpj,
            @NotBlank @Email @Size(max = 254) String email
    ) {
    }

    /** Pix copia e cola, linha digitável do boleto ou página de pagamento do provedor (cartão). */
    public record CobrancaResponse(
            UUID id,
            UUID nutricionistaId,
            String origem,
            UUID referenciaId,
            String descricao,
            StatusCobranca status,
            FormaPagamento formaPagamento,
            BigDecimal valor,
            LocalDate vencimento,
            String pixCopiaECola,
            String boletoLinhaDigitavel,
            String urlPagamento,
            Instant pagoEm,
            BigDecimal valorPago,
            String provedor,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static CobrancaResponse de(Cobranca c) {
            return new CobrancaResponse(c.getId(), c.getNutricionistaId(), c.getOrigem(), c.getReferenciaId(),
                    c.getDescricao(), c.getStatus(), c.getFormaPagamento(), c.getValor(), c.getVencimento(),
                    c.getPixCopiaECola(), c.getBoletoLinhaDigitavel(), c.getUrlPagamento(), c.getPagoEm(),
                    c.getValorPago(), c.getProvedor(), c.getCriadoEm(), c.getAtualizadoEm());
        }
    }
}
