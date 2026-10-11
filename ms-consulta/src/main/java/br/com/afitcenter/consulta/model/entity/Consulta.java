package br.com.afitcenter.consulta.model.entity;

import br.com.afitcenter.consulta.model.enums.Enums.Modalidade;
import br.com.afitcenter.consulta.model.enums.Enums.StatusConsulta;
import br.com.afitcenter.consulta.model.enums.Enums.TipoConsulta;
import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
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
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "consulta")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consulta extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    /** Nulo em atendimento online ou fora de consultório cadastrado. */
    @Column(name = "consultorio_id")
    private UUID consultorioId;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Column(name = "duracao_min", nullable = false)
    private Integer duracaoMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConsulta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modalidade modalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConsulta status;

    private BigDecimal valor;

    private String observacao;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    public Consulta(UUID nutricionistaId, UUID pacienteId) {
        this.nutricionistaId = nutricionistaId;
        this.pacienteId = pacienteId;
        this.status = StatusConsulta.AGENDADA;
    }

    public void preencher(UUID consultorioId, OffsetDateTime dataHora, Integer duracaoMin, TipoConsulta tipo,
                          Modalidade modalidade, BigDecimal valor, String observacao) {
        boolean mudouHorario = this.dataHora != null
                && (!this.dataHora.isEqual(dataHora) || !this.duracaoMin.equals(duracaoMin));
        if (mudouHorario && !status.permiteReagendar())
            throw new RegraNegocioException("Só é possível reagendar consultas agendadas ou confirmadas.");

        this.consultorioId = consultorioId;
        this.dataHora = dataHora;
        this.duracaoMin = duracaoMin;
        this.tipo = tipo;
        this.modalidade = modalidade;
        this.valor = valor;
        this.observacao = observacao;
    }

    public void alterarStatus(StatusConsulta novo) {
        if (novo == status) return;
        if (!status.proximosPermitidos().contains(novo))
            throw new RegraNegocioException("Não é possível mudar a consulta de " + status + " para " + novo + ".");
        this.status = novo;
    }

    public OffsetDateTime fim() {
        return dataHora.plusMinutes(duracaoMin);
    }

    public void excluir() {
        this.deletadoEm = Instant.now();
    }
}
