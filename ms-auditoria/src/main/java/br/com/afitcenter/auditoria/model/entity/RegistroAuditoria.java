package br.com.afitcenter.auditoria.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registro da trilha, só para leitura: a gravação é feita pelo {@code RegistroRepository} (com
 * {@code ON CONFLICT DO NOTHING}) e o banco recusa qualquer alteração.
 */
@Getter
@Entity
@Immutable
@Table(name = "registro_auditoria")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroAuditoria {

    /** O id do evento: reentregas da mesma mensagem não duplicam o registro. */
    @Id
    private UUID id;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Column(nullable = false)
    private String servico;

    @Column(nullable = false)
    private String acao;

    private String recurso;

    @Column(name = "recurso_id")
    private String recursoId;

    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(name = "nutricionista_id")
    private UUID nutricionistaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    private String papel;

    private String metodo;

    private String rota;

    private Integer status;

    private String ip;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> detalhes;
}
