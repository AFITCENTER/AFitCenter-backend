package br.com.afitcenter.dieta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Condição do ms-paciente (id do catálogo de lá, sem FK) que restringe um alimento ou um grupo
 * inteiro. Exatamente um dos dois vem preenchido.
 */
@Getter
@Entity
@Table(name = "condicao_restricao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CondicaoRestricao {

    @Id
    private Integer id;

    @Column(name = "condicao_id", nullable = false)
    private Integer condicaoId;

    @Column(name = "alimento_id")
    private Integer alimentoId;

    @Column(name = "grupo_alimentar_id")
    private Integer grupoAlimentarId;
}
