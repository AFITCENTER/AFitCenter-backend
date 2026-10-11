package br.com.afitcenter.paciente.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.paciente.model.enums.Enums.TipoMedicamento;
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

import java.time.LocalDate;
import java.util.UUID;

/** Medicamento, suplemento ou fitoterápico em uso (interações droga-nutriente). */
@Getter
@Entity
@Table(name = "paciente_medicamento")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PacienteMedicamento extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMedicamento tipo;

    @Column(nullable = false)
    private String nome;

    private String dosagem;

    private String frequencia;

    private LocalDate inicio;

    private LocalDate fim;

    private String observacao;

    public PacienteMedicamento(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    public void preencher(TipoMedicamento tipo, String nome, String dosagem, String frequencia, LocalDate inicio,
                          LocalDate fim, String observacao) {
        this.tipo = tipo;
        this.nome = nome;
        this.dosagem = dosagem;
        this.frequencia = frequencia;
        this.inicio = inicio;
        this.fim = fim;
        this.observacao = observacao;
    }
}
