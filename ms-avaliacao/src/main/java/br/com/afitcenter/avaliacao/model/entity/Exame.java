package br.com.afitcenter.avaliacao.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exame laboratorial (requisito 6), com resultados por métrica. Os anexos ficam em {@link ExameAnexo}. */
@Getter
@Entity
@Table(name = "exame")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exame extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    /** Opcional (exames costumam chegar entre consultas); validado no ms-consulta. */
    @Column(name = "consulta_id")
    private UUID consultaId;

    @Column(nullable = false)
    private String nome;

    private String laboratorio;

    @Column(name = "data_coleta", nullable = false)
    private LocalDate dataColeta;

    private String observacao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    @OneToMany(mappedBy = "exame", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExameResultado> resultados = new ArrayList<>();

    public Exame(UUID nutricionistaId, UUID pacienteId) {
        this.nutricionistaId = nutricionistaId;
        this.pacienteId = pacienteId;
    }

    public void preencher(UUID consultaId, String nome, String laboratorio, LocalDate dataColeta, String observacao) {
        this.consultaId = consultaId;
        this.nome = nome;
        this.laboratorio = laboratorio;
        this.dataColeta = dataColeta;
        this.observacao = observacao;
    }

    /** Substitui os resultados, atualizando no lugar os da mesma métrica (ver {@link Avaliacao#definirResultados}). */
    public void definirResultados(List<ExameResultado> novos) {
        resultados.removeIf(atual -> novos.stream().noneMatch(atual::mesmaMetrica));
        for (ExameResultado novo : novos) {
            resultados.stream().filter(novo::mesmaMetrica).findFirst()
                    .ifPresentOrElse(atual -> atual.copiarValores(novo), () -> resultados.add(novo));
        }
    }

    public void excluir() {
        this.deletadoEm = Instant.now();
    }
}
