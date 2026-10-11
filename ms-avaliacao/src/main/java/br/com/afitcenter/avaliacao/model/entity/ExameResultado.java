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
import java.util.UUID;

/** Um valor do exame (só métricas LABORATORIAL). */
@Getter
@Entity
@Table(name = "exame_resultado")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExameResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exame_id", nullable = false, updatable = false)
    private Exame exame;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false, updatable = false)
    private Metrica metrica;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "ref_min")
    private BigDecimal refMin;

    @Column(name = "ref_max")
    private BigDecimal refMax;

    private String observacao;

    public ExameResultado(Exame exame, Metrica metrica, BigDecimal valor, BigDecimal refMin, BigDecimal refMax,
                          String observacao) {
        this.exame = exame;
        this.metrica = metrica;
        this.valor = valor;
        this.refMin = refMin;
        this.refMax = refMax;
        this.observacao = observacao;
    }

    boolean mesmaMetrica(ExameResultado outro) {
        return metrica.getId().equals(outro.metrica.getId());
    }

    void copiarValores(ExameResultado outro) {
        this.valor = outro.valor;
        this.refMin = outro.refMin;
        this.refMax = outro.refMax;
        this.observacao = outro.observacao;
    }
}
