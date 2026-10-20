package br.com.afitcenter.pessoa.model.entity;

import br.com.afitcenter.pessoa.model.enums.FinalidadeToken;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Token de um link enviado por e-mail (redefinição de senha, confirmação do e-mail). De uso único e com validade.
 * Só o hash é guardado: quem lê o banco não consegue usar o link.
 */
@Getter
@Entity
@Table(name = "token_email")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pessoa_id", nullable = false)
    private UUID pessoaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinalidadeToken finalidade;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    public TokenEmail(UUID pessoaId, FinalidadeToken finalidade, String tokenHash, Instant expiraEm) {
        this.pessoaId = pessoaId;
        this.finalidade = finalidade;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.criadoEm = Instant.now();
    }

    public boolean expirado(Instant agora) {
        return !agora.isBefore(expiraEm);
    }
}
