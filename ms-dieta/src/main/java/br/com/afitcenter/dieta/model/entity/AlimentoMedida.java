package br.com.afitcenter.dieta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Medida caseira de um alimento: "1 colher de sopa cheia" = 25 g. Tabela sem colunas de auditoria. */
@Getter
@Entity
@Table(name = "alimento_medida")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlimentoMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "alimento_id", nullable = false, updatable = false)
    private Integer alimentoId;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private BigDecimal gramas;

    public AlimentoMedida(Integer alimentoId, String descricao, BigDecimal gramas) {
        this.alimentoId = alimentoId;
        atualizar(descricao, gramas);
    }

    public void atualizar(String descricao, BigDecimal gramas) {
        this.descricao = descricao;
        this.gramas = gramas;
    }
}
