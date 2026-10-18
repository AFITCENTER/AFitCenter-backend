package br.com.afitcenter.pagamentos.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.pagamentos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import br.com.afitcenter.pagamentos.provedor.DadosPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Uma cobrança no provedor de pagamento. {@code origem} + {@code referenciaId} dizem a quem ela pertence
 * (ex.: fatura de assinatura no ms-planos); o ms-pagamentos não interpreta essa referência.
 *
 * <p>Enquanto {@code idExterno} é nulo, a cobrança ainda não foi criada no provedor (a chamada falhou ou está
 * em andamento): repetir o pedido com a mesma chave de idempotência retoma a criação.</p>
 */
@Getter
@Entity
@Table(name = "cobranca")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cobranca extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(nullable = false, updatable = false)
    private String origem;

    @Column(name = "referencia_id", nullable = false, updatable = false)
    private UUID referenciaId;

    @Column(name = "chave_idempotencia", nullable = false, updatable = false)
    private String chaveIdempotencia;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, updatable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, updatable = false)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status;

    @Column(nullable = false, updatable = false)
    private String provedor;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "pix_copia_e_cola")
    private String pixCopiaECola;

    @Column(name = "boleto_linha_digitavel")
    private String boletoLinhaDigitavel;

    @Column(name = "url_pagamento")
    private String urlPagamento;

    @Column(name = "pago_em")
    private Instant pagoEm;

    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    /** Bloqueio otimista e ordem dos eventos {@code cobranca.*}. */
    @Version
    @Column(nullable = false)
    private Long versao;

    public Cobranca(UUID nutricionistaId, String origem, UUID referenciaId, String chaveIdempotencia, String descricao,
                    BigDecimal valor, LocalDate vencimento, FormaPagamento formaPagamento, String provedor) {
        this.nutricionistaId = nutricionistaId;
        this.origem = origem;
        this.referenciaId = referenciaId;
        this.chaveIdempotencia = chaveIdempotencia;
        this.descricao = descricao;
        this.valor = valor;
        this.vencimento = vencimento;
        this.formaPagamento = formaPagamento;
        this.provedor = provedor;
        this.status = StatusCobranca.PENDENTE;
    }

    public boolean criadaNoProvedor() {
        return idExterno != null;
    }

    public void registrarNoProvedor(DadosPagamento dados) {
        this.idExterno = dados.idExterno();
        this.pixCopiaECola = dados.pixCopiaECola();
        this.boletoLinhaDigitavel = dados.boletoLinhaDigitavel();
        this.urlPagamento = dados.urlPagamento();
    }

    public void pagar(Instant pagoEm, BigDecimal valorPago) {
        this.status = StatusCobranca.PAGA;
        this.pagoEm = pagoEm;
        this.valorPago = valorPago != null ? valorPago : valor;
    }

    public void encerrar(StatusCobranca status, Instant quando) {
        this.status = status;
        this.encerradaEm = quando;
    }
}
