package br.com.afitcenter.nutricionista.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Endereço guardado nas colunas da própria tabela dona (nutricionista ou consultório). */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Endereco {

    @Column(length = 8)
    private String cep;
    private String logradouro;
    @Column(length = 20)
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    @Column(length = 2)
    private String uf;
}
