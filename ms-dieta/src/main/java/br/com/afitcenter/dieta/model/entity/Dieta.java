package br.com.afitcenter.dieta.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.dieta.model.enums.Enums.StatusDieta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Plano alimentar de um paciente ou modelo reutilizável ({@code modelo = true}, sem paciente).
 * {@code dietaOrigemId} aponta para o modelo aplicado ou para a versão anterior.
 */
@Getter
@Entity
@Table(name = "dieta")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dieta extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(name = "paciente_id", updatable = false)
    private UUID pacienteId;

    @Column(name = "consulta_id")
    private UUID consultaId;

    @Column(name = "dieta_origem_id", updatable = false)
    private UUID dietaOrigemId;

    @Column(name = "is_modelo", nullable = false, updatable = false)
    private boolean modelo;

    @Column(nullable = false)
    private String titulo;

    private String objetivo;

    @Column(name = "kcal_meta")
    private BigDecimal kcalMeta;

    @Column(name = "proteina_meta_g")
    private BigDecimal proteinaMetaG;

    @Column(name = "carboidrato_meta_g")
    private BigDecimal carboidratoMetaG;

    @Column(name = "lipidio_meta_g")
    private BigDecimal lipidioMetaG;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusDieta status;

    private String observacao;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    private Dieta(UUID nutricionistaId, UUID pacienteId, boolean modelo, UUID dietaOrigemId) {
        this.nutricionistaId = nutricionistaId;
        this.pacienteId = pacienteId;
        this.modelo = modelo;
        this.dietaOrigemId = dietaOrigemId;
        this.status = StatusDieta.RASCUNHO;
    }

    public static Dieta doPaciente(UUID nutricionistaId, UUID pacienteId) {
        return new Dieta(nutricionistaId, pacienteId, false, null);
    }

    public static Dieta modelo(UUID nutricionistaId) {
        return new Dieta(nutricionistaId, null, true, null);
    }

    /** Cópia dos dados gerais de {@code origem} para outro paciente (modelo aplicado) ou nova versão. */
    public static Dieta copiaPara(Dieta origem, UUID pacienteId, UUID consultaId) {
        Dieta copia = new Dieta(origem.nutricionistaId, pacienteId, false, origem.id);
        copia.preencher(consultaId, origem.titulo, origem.objetivo, origem.kcalMeta, origem.proteinaMetaG,
                origem.carboidratoMetaG, origem.lipidioMetaG, null, null, origem.observacao);
        return copia;
    }

    public void preencher(UUID consultaId, String titulo, String objetivo, BigDecimal kcalMeta, BigDecimal proteinaMetaG,
                          BigDecimal carboidratoMetaG, BigDecimal lipidioMetaG, LocalDate dataInicio, LocalDate dataFim,
                          String observacao) {
        if (dataInicio != null && dataFim != null && dataFim.isBefore(dataInicio))
            throw new RegraNegocioException("A data de fim deve ser igual ou posterior à de início.");
        this.consultaId = consultaId;
        this.titulo = titulo;
        this.objetivo = objetivo;
        this.kcalMeta = kcalMeta;
        this.proteinaMetaG = proteinaMetaG;
        this.carboidratoMetaG = carboidratoMetaG;
        this.lipidioMetaG = lipidioMetaG;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.observacao = observacao;
    }

    public void alterarStatus(StatusDieta novo) {
        if (modelo)
            throw new RegraNegocioException("Modelos não têm status. Aplique o modelo a um paciente.");
        if (novo == status) return;
        if (!status.proximosPermitidos().contains(novo))
            throw new RegraNegocioException("Não é possível mudar a dieta de " + status + " para " + novo + ".");
        this.status = novo;
    }

    /** Dieta encerrada é histórico: refeições, itens e dados gerais ficam congelados. */
    public void exigirEditavel() {
        if (status == StatusDieta.ENCERRADA)
            throw new RegraNegocioException("A dieta está encerrada e não pode ser alterada. Crie uma nova versão.");
    }

    public void excluir() {
        this.deletadoEm = Instant.now();
    }
}
