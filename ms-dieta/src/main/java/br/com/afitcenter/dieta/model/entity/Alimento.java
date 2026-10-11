package br.com.afitcenter.dieta.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.dieta.model.enums.Enums.FonteAlimento;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Alimento do catálogo. {@code nutricionistaId} nulo = base oficial (TACO/TBCA), visível a todos;
 * preenchido = alimento cadastrado pelo nutricionista, visível só a ele. Macros na porção base,
 * em colunas, porque são lidos o tempo todo para somar a dieta.
 */
@Getter
@Entity
@Table(name = "alimento")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alimento extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nutricionista_id", updatable = false)
    private UUID nutricionistaId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grupo_alimentar_id")
    private GrupoAlimentar grupo;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FonteAlimento fonte;

    @Column(name = "codigo_fonte")
    private String codigoFonte;

    @Column(name = "porcao_base_g", nullable = false)
    private BigDecimal porcaoBaseG;

    private BigDecimal kcal;

    @Column(name = "proteina_g")
    private BigDecimal proteinaG;

    @Column(name = "carboidrato_g")
    private BigDecimal carboidratoG;

    @Column(name = "lipidio_g")
    private BigDecimal lipidioG;

    @Column(name = "fibra_g")
    private BigDecimal fibraG;

    @Column(nullable = false)
    private boolean ativo;

    /** Micronutrientes na porção base: id do nutriente → quantidade. */
    @ElementCollection
    @CollectionTable(name = "alimento_nutriente", joinColumns = @JoinColumn(name = "alimento_id"))
    @MapKeyColumn(name = "nutriente_id")
    @Column(name = "quantidade", nullable = false)
    private Map<Integer, BigDecimal> nutrientes = new HashMap<>();

    /** Alimento próprio do nutricionista. */
    public Alimento(UUID nutricionistaId) {
        this.nutricionistaId = nutricionistaId;
        this.fonte = FonteAlimento.PROPRIO;
        this.ativo = true;
    }

    public void preencher(GrupoAlimentar grupo, String nome, BigDecimal porcaoBaseG, BigDecimal kcal,
                          BigDecimal proteinaG, BigDecimal carboidratoG, BigDecimal lipidioG, BigDecimal fibraG,
                          Map<Integer, BigDecimal> nutrientes) {
        this.grupo = grupo;
        this.nome = nome;
        this.porcaoBaseG = porcaoBaseG;
        this.kcal = kcal;
        this.proteinaG = proteinaG;
        this.carboidratoG = carboidratoG;
        this.lipidioG = lipidioG;
        this.fibraG = fibraG;
        this.nutrientes.clear();
        this.nutrientes.putAll(nutrientes);
    }

    public boolean proprio() {
        return nutricionistaId != null;
    }

    /** Exclusão lógica: itens de dietas que já usam o alimento continuam válidos. */
    public void desativar() {
        this.ativo = false;
    }
}
