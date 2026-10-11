package br.com.afitcenter.dieta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Grupos da TACO (cereais, leguminosas, carnes...). Base também das listas de substituição. */
@Getter
@Entity
@Table(name = "grupo_alimentar")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrupoAlimentar {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;
}
