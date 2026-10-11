package br.com.afitcenter.dieta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Micronutrientes das colunas da TACO. {@code vdReferencia} ainda vazio (IN ANVISA nº 75/2020). */
@Getter
@Entity
@Table(name = "nutriente")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nutriente {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String unidade;

    @Column(name = "vd_referencia")
    private BigDecimal vdReferencia;
}
