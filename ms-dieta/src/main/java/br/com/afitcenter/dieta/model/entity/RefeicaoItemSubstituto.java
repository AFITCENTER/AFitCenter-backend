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
import java.util.UUID;

/** Alimento que pode substituir o item ("ou 1 pão francês"), com a própria quantidade. */
@Getter
@Entity
@Table(name = "refeicao_item_substituto")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefeicaoItemSubstituto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "refeicao_item_id", nullable = false, updatable = false)
    private UUID refeicaoItemId;

    @Column(name = "alimento_id", nullable = false)
    private Integer alimentoId;

    @Column(nullable = false)
    private BigDecimal quantidade;

    @Column(name = "alimento_medida_id")
    private Integer alimentoMedidaId;

    @Column(name = "quantidade_g", nullable = false)
    private BigDecimal quantidadeG;

    public RefeicaoItemSubstituto(UUID refeicaoItemId) {
        this.refeicaoItemId = refeicaoItemId;
    }

    public void preencher(Integer alimentoId, BigDecimal quantidade, Integer alimentoMedidaId, BigDecimal quantidadeG) {
        this.alimentoId = alimentoId;
        this.quantidade = quantidade;
        this.alimentoMedidaId = alimentoMedidaId;
        this.quantidadeG = quantidadeG;
    }

    public RefeicaoItemSubstituto copiarPara(UUID refeicaoItemId) {
        RefeicaoItemSubstituto copia = new RefeicaoItemSubstituto(refeicaoItemId);
        copia.preencher(alimentoId, quantidade, alimentoMedidaId, quantidadeG);
        return copia;
    }
}
