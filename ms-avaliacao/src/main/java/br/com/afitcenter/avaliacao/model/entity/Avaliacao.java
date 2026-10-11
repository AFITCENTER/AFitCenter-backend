package br.com.afitcenter.avaliacao.model.entity;

import br.com.afitcenter.avaliacao.model.enums.Enums.MetodoAvaliacao;
import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Avaliação corporal (requisito 3). Peso e altura são colunas e o IMC é calculado pelo banco; os
 * demais valores do laudo são {@link AvaliacaoResultado}s ligados ao catálogo de métricas.
 */
@Getter
@Entity
@Table(name = "avaliacao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Avaliacao extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    @Column(name = "paciente_id", nullable = false, updatable = false)
    private UUID pacienteId;

    /** Opcional; validado no ms-consulta. */
    @Column(name = "consulta_id")
    private UUID consultaId;

    @Column(name = "equipamento_id")
    private Integer equipamentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoAvaliacao metodo;

    @Column(name = "data_hora_medicao", nullable = false)
    private OffsetDateTime dataHoraMedicao;

    @Column(name = "peso_kg", nullable = false)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm", nullable = false)
    private BigDecimal alturaCm;

    /** Coluna gerada no banco: só tem valor depois de recarregar a entidade ({@code refresh}) após gravar. */
    @Column(insertable = false, updatable = false)
    private BigDecimal imc;

    @Column(name = "em_jejum")
    private Boolean emJejum;

    private String observacao;

    @Column(name = "laudo_url")
    private String laudoUrl;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    @OneToMany(mappedBy = "avaliacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AvaliacaoResultado> resultados = new ArrayList<>();

    public Avaliacao(UUID nutricionistaId, UUID pacienteId) {
        this.nutricionistaId = nutricionistaId;
        this.pacienteId = pacienteId;
    }

    public void preencher(UUID consultaId, Integer equipamentoId, MetodoAvaliacao metodo,
                          OffsetDateTime dataHoraMedicao, BigDecimal pesoKg, BigDecimal alturaCm, Boolean emJejum,
                          String observacao, String laudoUrl) {
        this.consultaId = consultaId;
        this.equipamentoId = equipamentoId;
        this.metodo = metodo;
        this.dataHoraMedicao = dataHoraMedicao;
        this.pesoKg = pesoKg;
        this.alturaCm = alturaCm;
        this.emJejum = emJejum;
        this.observacao = observacao;
        this.laudoUrl = laudoUrl;
    }

    /**
     * Substitui os resultados do laudo. O que já existe na mesma métrica e região é atualizado no
     * lugar: apagar e reinserir violaria a unicidade {@code (avaliacao_id, metrica_id, regiao_id)},
     * porque o Hibernate executa os inserts antes dos deletes.
     */
    public void definirResultados(List<AvaliacaoResultado> novos) {
        resultados.removeIf(atual -> novos.stream().noneMatch(atual::mesmaPosicao));
        for (AvaliacaoResultado novo : novos) {
            resultados.stream().filter(novo::mesmaPosicao).findFirst()
                    .ifPresentOrElse(atual -> atual.copiarValores(novo), () -> resultados.add(novo));
        }
    }

    public void excluir() {
        this.deletadoEm = Instant.now();
    }
}
