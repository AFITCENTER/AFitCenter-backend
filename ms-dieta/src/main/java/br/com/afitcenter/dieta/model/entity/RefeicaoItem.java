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

/**
 * Alimento numa refeição, na quantidade da medida escolhida (ou em gramas, sem medida). Itens com o
 * mesmo {@code opcao} formam uma opção da refeição ("Opção 1", "Opção 2"). {@code quantidadeG} é
 * gravado na inclusão: mudar a medida depois não altera dietas existentes.
 */
@Getter
@Entity
@Table(name = "refeicao_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefeicaoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "refeicao_id", nullable = false, updatable = false)
    private UUID refeicaoId;

    @Column(name = "alimento_id", nullable = false)
    private Integer alimentoId;

    @Column(nullable = false)
    private Integer opcao;

    @Column(nullable = false)
    private BigDecimal quantidade;

    @Column(name = "alimento_medida_id")
    private Integer alimentoMedidaId;

    @Column(name = "quantidade_g", nullable = false)
    private BigDecimal quantidadeG;

    @Column(nullable = false)
    private Integer ordem;

    private String observacao;

    public RefeicaoItem(UUID refeicaoId) {
        this.refeicaoId = refeicaoId;
    }

    public void preencher(Integer alimentoId, int opcao, BigDecimal quantidade, Integer alimentoMedidaId,
                          BigDecimal quantidadeG, int ordem, String observacao) {
        this.alimentoId = alimentoId;
        this.opcao = opcao;
        this.quantidade = quantidade;
        this.alimentoMedidaId = alimentoMedidaId;
        this.quantidadeG = quantidadeG;
        this.ordem = ordem;
        this.observacao = observacao;
    }

    public RefeicaoItem copiarPara(UUID refeicaoId) {
        RefeicaoItem copia = new RefeicaoItem(refeicaoId);
        copia.preencher(alimentoId, opcao, quantidade, alimentoMedidaId, quantidadeG, ordem, observacao);
        return copia;
    }
}
