package br.com.afitcenter.paciente.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Item do catálogo de condições. {@code nutricionistaId} nulo = catálogo do sistema (visível a todos);
 * preenchido = condição cadastrada pelo próprio nutricionista (visível só a ele).
 */
@Getter
@Entity
@Table(name = "condicao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Condicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_condicao_id")
    private TipoCondicao tipo;

    @Column(name = "nutricionista_id", updatable = false)
    private UUID nutricionistaId;

    @Column(nullable = false)
    private String nome;

    private String descricao;

    public Condicao(TipoCondicao tipo, UUID nutricionistaId, String nome, String descricao) {
        this.tipo = tipo;
        this.nutricionistaId = nutricionistaId;
        this.nome = nome;
        this.descricao = descricao;
    }

    public void atualizar(TipoCondicao tipo, String nome, String descricao) {
        this.tipo = tipo;
        this.nome = nome;
        this.descricao = descricao;
    }

    public boolean isDoSistema() {
        return nutricionistaId == null;
    }
}
