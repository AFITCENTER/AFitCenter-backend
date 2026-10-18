package br.com.afitcenter.planos.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * O que está sendo cobrado (plano, ciclo, período). Como é cobrado fica no ms-pagamentos: a fatura aponta
 * para a cobrança vigente ({@code cobrancaId}) e a cobrança aponta de volta para a fatura (referência).
 *
 * <p>Uma fatura pode ter várias cobranças ao longo do tempo (Pix vencido, cartão recusado, troca de forma de
 * pagamento), uma de cada vez. {@code tentativasCobranca} forma a chave de idempotência de cada uma.</p>
 */
@Getter
@Entity
@Table(name = "fatura")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fatura extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assinatura_id", nullable = false, updatable = false)
    private UUID assinaturaId;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoFatura tipo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plano_id", updatable = false)
    private Plano plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Ciclo ciclo;

    @Column(nullable = false, updatable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private LocalDate vencimento;

    /** Renovação: definido na emissão. Mudança de plano: definido no pagamento (o período começa nele). */
    @Column(name = "periodo_inicio")
    private Instant periodoInicio;

    @Column(name = "periodo_fim")
    private Instant periodoFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusFatura status;

    @Column(name = "cobranca_id")
    private UUID cobrancaId;

    @Column(name = "tentativas_cobranca", nullable = false)
    private int tentativasCobranca;

    @Column(name = "paga_em")
    private Instant pagaEm;

    @Version
    @Column(nullable = false)
    private Long versao;

    private Fatura(Assinatura assinatura, TipoFatura tipo, Plano plano, Ciclo ciclo, BigDecimal valor,
                   FormaPagamento formaPagamento, LocalDate vencimento) {
        this.assinaturaId = assinatura.getId();
        this.nutricionistaId = assinatura.getNutricionistaId();
        this.tipo = tipo;
        this.plano = plano;
        this.ciclo = ciclo;
        this.valor = valor;
        this.formaPagamento = formaPagamento;
        this.vencimento = vencimento;
        this.status = StatusFatura.ABERTA;
        this.tentativasCobranca = 1;
    }

    public static Fatura mudancaDePlano(Assinatura assinatura, Plano plano, Ciclo ciclo, BigDecimal valor,
                                        FormaPagamento formaPagamento, LocalDate vencimento) {
        return new Fatura(assinatura, TipoFatura.MUDANCA_PLANO, plano, ciclo, valor, formaPagamento, vencimento);
    }

    public static Fatura renovacao(Assinatura assinatura, Ciclo ciclo, BigDecimal valor, LocalDate vencimento,
                                   Instant inicio, Instant fim) {
        Fatura fatura = new Fatura(assinatura, TipoFatura.RENOVACAO, assinatura.getPlano(), ciclo, valor,
                assinatura.getFormaPagamento(), vencimento);
        fatura.periodoInicio = inicio;
        fatura.periodoFim = fim;
        return fatura;
    }

    public boolean aberta() {
        return status == StatusFatura.ABERTA;
    }

    /** Prepara uma nova cobrança: a anterior (se houver) já foi cancelada no ms-pagamentos. */
    public void novaTentativa(FormaPagamento formaPagamento, LocalDate vencimento) {
        this.formaPagamento = formaPagamento;
        this.vencimento = vencimento;
        this.cobrancaId = null;
        this.tentativasCobranca++;
    }

    /** Mesma tentativa = mesma chave: repetir a chamada ao ms-pagamentos não cria outra cobrança. */
    public String chaveIdempotencia() {
        return "fatura:" + id + ":" + tentativasCobranca;
    }

    public void vincularCobranca(UUID cobrancaId) {
        this.cobrancaId = cobrancaId;
    }

    public void pagar(Instant pagaEm, Instant inicio, Instant fim) {
        this.status = StatusFatura.PAGA;
        this.pagaEm = pagaEm;
        this.periodoInicio = inicio;
        this.periodoFim = fim;
    }

    public void cancelar() {
        this.status = StatusFatura.CANCELADA;
    }

    public void estornar() {
        this.status = StatusFatura.ESTORNADA;
    }
}
