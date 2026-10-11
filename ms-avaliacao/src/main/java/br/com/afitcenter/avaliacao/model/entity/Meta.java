package br.com.afitcenter.avaliacao.model.entity;

import br.com.afitcenter.avaliacao.model.enums.Enums.StatusMeta;
import br.com.afitcenter.core.base.model.EntidadeAuditavel;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Valor-alvo de uma métrica do corpo inteiro para o paciente (ex.: PESO 95 kg até março). Não tem
 * exclusão lógica: para desistir de uma meta, prefira o status CANCELADA.
 */
@Getter
@Entity
@Table(name = "meta")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meta extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false)
    private Metrica metrica;

    @Column(name = "valor_alvo", nullable = false)
    private BigDecimal valorAlvo;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMeta status;

    private String observacao;

    public Meta(UUID nutricionistaId, UUID pacienteId) {
        this.nutricionistaId = nutricionistaId;
        this.pacienteId = pacienteId;
        this.status = StatusMeta.ATIVA;
    }

    public void preencher(Metrica metrica, BigDecimal valorAlvo, LocalDate dataInicio, LocalDate prazo,
                          StatusMeta status, String observacao) {
        this.metrica = metrica;
        this.valorAlvo = valorAlvo;
        this.dataInicio = dataInicio;
        this.prazo = prazo;
        if (status != null) this.status = status;
        this.observacao = observacao;
    }
}
