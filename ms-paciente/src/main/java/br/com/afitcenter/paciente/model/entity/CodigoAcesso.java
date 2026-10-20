package br.com.afitcenter.paciente.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Código que o nutricionista entrega ao paciente para ligar a ficha à conta dele no portal. Um por ficha, de uso
 * único e com validade. Só o hash é guardado: quem lê o banco não consegue usar o código.
 */
@Getter
@Entity
@Table(name = "codigo_acesso")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodigoAcesso {

    @Id
    @Column(name = "paciente_id")
    private UUID pacienteId;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public CodigoAcesso(UUID pacienteId) {
        this.pacienteId = pacienteId;
    }

    /** Um código novo substitui o anterior, que deixa de valer. */
    public void renovar(String codigoHash, Instant expiraEm) {
        this.codigoHash = codigoHash;
        this.expiraEm = expiraEm;
        this.criadoEm = Instant.now();
    }

    public boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }
}
