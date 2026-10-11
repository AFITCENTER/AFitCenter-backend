package br.com.afitcenter.avaliacao.model.entity;

import br.com.afitcenter.avaliacao.model.enums.Enums.TipoEquipamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Aparelho usado na avaliação. Sem nutricionista = catálogo do sistema (visível para todos, só
 * leitura); com nutricionista = cadastrado por ele e visível só para ele.
 */
@Getter
@Entity
@Table(name = "equipamento")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nutricionista_id", updatable = false)
    private UUID nutricionistaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEquipamento tipo;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    public Equipamento(UUID nutricionistaId) {
        this.nutricionistaId = nutricionistaId;
    }

    public void preencher(TipoEquipamento tipo, String marca, String modelo) {
        this.tipo = tipo;
        this.marca = marca;
        this.modelo = modelo;
    }

    public boolean isDoSistema() {
        return nutricionistaId == null;
    }
}
