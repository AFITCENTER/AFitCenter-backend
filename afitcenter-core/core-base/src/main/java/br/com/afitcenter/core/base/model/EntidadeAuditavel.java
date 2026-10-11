package br.com.afitcenter.core.base.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

/**
 * Superclasse JPA com as colunas de auditoria {@code criado_em} e {@code atualizado_em}.
 *
 * <p>O banco também atualiza {@code atualizado_em} por trigger; preencher aqui mantém o
 * valor correto na entidade sem precisar recarregá-la.</p>
 */
@Getter
@MappedSuperclass
public abstract class EntidadeAuditavel {

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    protected void aoCriar() {
        Instant agora = Instant.now();
        if (criadoEm == null) criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    protected void aoAtualizar() {
        atualizadoEm = Instant.now();
    }
}
