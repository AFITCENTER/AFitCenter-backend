package br.com.afitcenter.nutricionista.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "consultorio")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultorio extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(nullable = false)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Embedded
    private Endereco endereco;

    @Column(nullable = false)
    private boolean ativo;

    /** Bloqueio otimista e ordem dos eventos para as réplicas. */
    @Version
    @Column(nullable = false)
    private Long versao;

    public Consultorio(UUID nutricionistaId, String nome, String telefone, Endereco endereco) {
        this.nutricionistaId = nutricionistaId;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
        this.ativo = true;
    }

    public void atualizar(String nome, String telefone, Endereco endereco) {
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    public void desativar() {
        this.ativo = false;
    }
}
