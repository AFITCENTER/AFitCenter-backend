package br.com.afitcenter.avaliacao.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Um valor do laudo. Região nula = valor do corpo inteiro. */
@Getter
@Entity
@Table(name = "avaliacao_resultado")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvaliacaoResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avaliacao_id", nullable = false, updatable = false)
    private Avaliacao avaliacao;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false, updatable = false)
    private Metrica metrica;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "regiao_id", updatable = false)
    private RegiaoCorporal regiao;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "ref_min")
    private BigDecimal refMin;

    @Column(name = "ref_max")
    private BigDecimal refMax;

    private String classificacao;

    public AvaliacaoResultado(Avaliacao avaliacao, Metrica metrica, RegiaoCorporal regiao, BigDecimal valor,
                              BigDecimal refMin, BigDecimal refMax, String classificacao) {
        this.avaliacao = avaliacao;
        this.metrica = metrica;
        this.regiao = regiao;
        this.valor = valor;
        this.refMin = refMin;
        this.refMax = refMax;
        this.classificacao = classificacao;
    }

    /** Mesma métrica e mesma região (ou os dois do corpo inteiro). */
    boolean mesmaPosicao(AvaliacaoResultado outro) {
        return metrica.getId().equals(outro.metrica.getId()) && Objects.equals(regiaoId(), outro.regiaoId());
    }

    void copiarValores(AvaliacaoResultado outro) {
        this.valor = outro.valor;
        this.refMin = outro.refMin;
        this.refMax = outro.refMax;
        this.classificacao = outro.classificacao;
    }

    private Integer regiaoId() {
        return regiao == null ? null : regiao.getId();
    }
}
