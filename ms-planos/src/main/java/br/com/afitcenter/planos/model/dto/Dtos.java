package br.com.afitcenter.planos.model.dto;

import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.entity.Plano;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos HTTP do ms-planos. */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- catálogo

    public record PlanoResponse(
            Integer id,
            String codigo,
            String nome,
            String descricao,
            BigDecimal precoMensal,
            BigDecimal precoAnual,
            Integer limitePacientes,
            Integer limiteConsultorios,
            boolean gratuito,
            boolean padrao,
            boolean publico,
            boolean ativo,
            int ordem
    ) {

        public static PlanoResponse de(Plano p) {
            return new PlanoResponse(p.getId(), p.getCodigo(), p.getNome(), p.getDescricao(), p.getPrecoMensal(),
                    p.getPrecoAnual(), p.getLimitePacientes(), p.getLimiteConsultorios(), p.gratuito(), p.isPadrao(),
                    p.isPublico(), p.isAtivo(), p.getOrdem());
        }
    }

    /** Limites nulos = ilimitado. Preço mensal zero = plano gratuito (sem preço anual). */
    public record PlanoRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}", message = "Use letras maiúsculas, números e _.")
            String codigo,
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 1000) String descricao,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal precoMensal,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal precoAnual,
            @Min(0) Integer limitePacientes,
            @Min(0) Integer limiteConsultorios,
            Boolean publico,
            Integer ordem
    ) {

        public boolean publicoOuPadrao() {
            return publico == null || publico;
        }

        public int ordemOuPadrao() {
            return ordem == null ? 0 : ordem;
        }
    }

    public record AtivoRequest(@NotNull Boolean ativo) {
    }

    // ---------------------------------------------------------------- assinatura

    public record PlanoResumo(Integer id, String codigo, String nome) {

        public static PlanoResumo de(Plano p) {
            return p == null ? null : new PlanoResumo(p.getId(), p.getCodigo(), p.getNome());
        }
    }

    /** Limites que valem agora (assinatura suspensa: os do plano padrão). Nulo = ilimitado. */
    public record LimitesResponse(Integer pacientes, Integer consultorios) {
    }

    public record AssinaturaResponse(
            UUID id,
            UUID nutricionistaId,
            PlanoResumo plano,
            Ciclo ciclo,
            FormaPagamento formaPagamento,
            StatusAssinatura status,
            Instant periodoInicio,
            Instant periodoFim,
            PlanoResumo planoAgendado,
            LimitesResponse limites,
            Instant atualizadoEm
    ) {

        public static AssinaturaResponse de(Assinatura a, Plano efetivo) {
            return new AssinaturaResponse(a.getId(), a.getNutricionistaId(), PlanoResumo.de(a.getPlano()), a.getCiclo(),
                    a.getFormaPagamento(), a.getStatus(), a.getPeriodoInicio(), a.getPeriodoFim(),
                    PlanoResumo.de(a.getPlanoAgendado()),
                    new LimitesResponse(efetivo.getLimitePacientes(), efetivo.getLimiteConsultorios()),
                    a.getAtualizadoEm());
        }
    }

    /** Ciclo e forma de pagamento só são exigidos para planos pagos. */
    public record MudancaPlanoRequest(@NotNull Integer planoId, Ciclo ciclo, FormaPagamento formaPagamento) {
    }

    /**
     * Plano gratuito: a mudança já valeu (ou foi agendada para o fim do período pago) e não há fatura.
     * Plano pago: a fatura e a cobrança com os dados para pagar; o plano muda quando o pagamento for confirmado.
     */
    public record MudancaPlanoResponse(AssinaturaResponse assinatura, FaturaResponse fatura, CobrancaResponse cobranca) {
    }

    public record NovaCobrancaRequest(@NotNull FormaPagamento formaPagamento) {
    }

    public record FaturaResponse(
            UUID id,
            TipoFatura tipo,
            PlanoResumo plano,
            Ciclo ciclo,
            BigDecimal valor,
            FormaPagamento formaPagamento,
            LocalDate vencimento,
            Instant periodoInicio,
            Instant periodoFim,
            StatusFatura status,
            UUID cobrancaId,
            Instant pagaEm,
            Instant criadoEm
    ) {

        public static FaturaResponse de(Fatura f) {
            return new FaturaResponse(f.getId(), f.getTipo(), PlanoResumo.de(f.getPlano()), f.getCiclo(), f.getValor(),
                    f.getFormaPagamento(), f.getVencimento(), f.getPeriodoInicio(), f.getPeriodoFim(), f.getStatus(),
                    f.getCobrancaId(), f.getPagaEm(), f.getCriadoEm());
        }
    }

    // ---------------------------------------------------------------- contrato com o ms-pagamentos

    public record CriarCobrancaRequest(
            String chaveIdempotencia,
            UUID nutricionistaId,
            String origem,
            UUID referenciaId,
            String descricao,
            BigDecimal valor,
            LocalDate vencimento,
            FormaPagamento formaPagamento,
            Pagador pagador
    ) {
    }

    /** O provedor de pagamento exige nome e CPF/CNPJ para emitir Pix e boleto. */
    public record Pagador(String nome, String cpfCnpj, String email) {
    }

    /** Dados para pagar: Pix copia e cola, linha digitável do boleto ou página do provedor (cartão). */
    public record CobrancaResponse(
            UUID id,
            String status,
            FormaPagamento formaPagamento,
            BigDecimal valor,
            LocalDate vencimento,
            String pixCopiaECola,
            String boletoLinhaDigitavel,
            String urlPagamento
    ) {
    }

    // ---------------------------------------------------------------- contrato com o ms-pessoa

    /** Formato de {@code GET /internal/pessoas/{id}}. */
    public record PessoaInternoResponse(UUID id, String nome, String cpf, String email) {
    }

    // ---------------------------------------------------------------- rota interna

    /** Formato de {@code GET /internal/assinaturas/{nutricionistaId}} (o {@code AssinaturaRef} do core-assinatura). */
    public record AssinaturaInternoResponse(
            UUID nutricionistaId,
            UUID assinaturaId,
            String planoCodigo,
            String status,
            Integer limitePacientes,
            Integer limiteConsultorios,
            long versao
    ) {

        public static AssinaturaInternoResponse de(Assinatura a, Plano efetivo) {
            return new AssinaturaInternoResponse(a.getNutricionistaId(), a.getId(), a.getPlano().getCodigo(),
                    a.getStatus().name(), efetivo.getLimitePacientes(), efetivo.getLimiteConsultorios(), a.getVersao());
        }
    }
}
