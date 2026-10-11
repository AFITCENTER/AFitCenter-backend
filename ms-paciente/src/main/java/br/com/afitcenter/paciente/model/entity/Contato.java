package br.com.afitcenter.paciente.model.entity;

import br.com.afitcenter.paciente.model.enums.Enums.TipoContato;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Contato do paciente (tabela paciente_contato). Ex.: "telefone da mãe" vai em observação. */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contato {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoContato tipo;

    @Column(nullable = false)
    private String valor;

    @Column(nullable = false)
    private boolean principal;

    private String observacao;
}
