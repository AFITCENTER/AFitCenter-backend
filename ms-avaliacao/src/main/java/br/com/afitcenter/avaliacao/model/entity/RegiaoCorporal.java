package br.com.afitcenter.avaliacao.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Segmento da bioimpedância (braço, tronco, perna), ponto de perímetro ou de dobra cutânea. */
@Getter
@Entity
@Table(name = "regiao_corporal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegiaoCorporal {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String tipo;
}
