package br.com.afitcenter.consulta.model.entity;

import br.com.afitcenter.consulta.model.enums.Enums.CategoriaAnalise;
import br.com.afitcenter.core.base.model.EntidadeAuditavel;
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

import java.time.Instant;
import java.util.UUID;

/** Registro clínico do nutricionista na consulta: anamnese, queixa, achado, diagnóstico, conduta. */
@Getter
@Entity
@Table(name = "analise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analise extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consulta_id", nullable = false, updatable = false)
    private UUID consultaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaAnalise categoria;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    public Analise(UUID consultaId, CategoriaAnalise categoria, String descricao) {
        this.consultaId = consultaId;
        this.categoria = categoria;
        this.descricao = descricao;
    }

    public void atualizar(CategoriaAnalise categoria, String descricao) {
        this.categoria = categoria;
        this.descricao = descricao;
    }

    public void excluir() {
        this.deletadoEm = Instant.now();
    }
}
