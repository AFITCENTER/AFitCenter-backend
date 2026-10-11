package br.com.afitcenter.paciente.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Alergia, intolerância, doença, restrição física... (catálogo fixo, carregado na migração). */
@Getter
@Entity
@Table(name = "tipo_condicao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoCondicao {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;
}
