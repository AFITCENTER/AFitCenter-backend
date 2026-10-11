package br.com.afitcenter.paciente.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.paciente.model.enums.Enums.Gravidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.util.UUID;

/** Condição de um paciente, com os detalhes que são dele (gravidade, desde quando, se está ativa). */
@Getter
@Entity
@Table(name = "paciente_condicao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PacienteCondicao extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "condicao_id", updatable = false)
    private Condicao condicao;

    @Enumerated(EnumType.STRING)
    private Gravidade gravidade;

    private LocalDate desde;

    @Column(nullable = false)
    private boolean ativa;

    private String observacao;

    public PacienteCondicao(UUID pacienteId, Condicao condicao) {
        this.pacienteId = pacienteId;
        this.condicao = condicao;
        this.ativa = true;
    }

    public void atualizar(Gravidade gravidade, LocalDate desde, boolean ativa, String observacao) {
        this.gravidade = gravidade;
        this.desde = desde;
        this.ativa = ativa;
        this.observacao = observacao;
    }
}
