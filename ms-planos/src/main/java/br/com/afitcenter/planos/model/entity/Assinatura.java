package br.com.afitcenter.planos.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Assinatura do nutricionista: uma por tenant, criada no plano padrão na primeira vez que alguém pergunta
 * por ela. Nunca é apagada: cancelar agenda a volta ao plano padrão no fim do período pago.
 */
@Getter
@Entity
@Table(name = "assinatura")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assinatura extends EntidadeAuditavel {

    @Id
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    /** Nulo no plano gratuito. */
    @Enumerated(EnumType.STRING)
    private Ciclo ciclo;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status;

    /** Período pago atual; nulos no plano gratuito, que não vence. */
    @Column(name = "periodo_inicio")
    private Instant periodoInicio;

    @Column(name = "periodo_fim")
    private Instant periodoFim;

    /** Plano que passa a valer no fim do período (cancelamento = plano padrão). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plano_agendado_id")
    private Plano planoAgendado;

    /** Bloqueio otimista e ordem dos eventos para as réplicas. */
    @Version
    @Column(nullable = false)
    private Long versao;

    public boolean pagaNoPeriodo() {
        return !plano.gratuito() && periodoFim != null;
    }

    public boolean cancelamentoAgendado() {
        return planoAgendado != null;
    }

    /** Começa um período pago (pagamento de mudança de plano ou de renovação). */
    public void iniciarPeriodo(Plano plano, Ciclo ciclo, FormaPagamento formaPagamento, Instant inicio, Instant fim) {
        this.plano = plano;
        this.ciclo = ciclo;
        this.formaPagamento = formaPagamento;
        this.periodoInicio = inicio;
        this.periodoFim = fim;
        this.status = StatusAssinatura.ATIVA;
        this.planoAgendado = null;
    }

    /** Plano gratuito, sem período: cancelamento efetivado, estorno ou troca entre planos gratuitos. */
    public void mudarParaGratuito(Plano plano) {
        this.plano = plano;
        this.ciclo = null;
        this.formaPagamento = null;
        this.periodoInicio = null;
        this.periodoFim = null;
        this.status = StatusAssinatura.ATIVA;
        this.planoAgendado = null;
    }

    public void agendarMudanca(Plano plano) {
        this.planoAgendado = plano;
    }

    public void desfazerAgendamento() {
        this.planoAgendado = null;
    }

    public void marcarInadimplente() {
        this.status = StatusAssinatura.INADIMPLENTE;
    }

    public void suspender() {
        this.status = StatusAssinatura.SUSPENSA;
    }

    public void alterarFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }
}
