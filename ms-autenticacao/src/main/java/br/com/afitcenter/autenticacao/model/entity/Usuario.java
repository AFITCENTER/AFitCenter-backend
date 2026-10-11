package br.com.afitcenter.autenticacao.model.entity;

import br.com.afitcenter.autenticacao.model.enums.StatusUsuario;
import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.core.base.security.Papel;
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
 * Credencial de acesso. O id do usuário nutricionista também é o id do perfil no ms-nutricionista
 * e o tenant de todos os dados dele.
 */
@Getter
@Entity
@Table(name = "usuario")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Sempre minúsculo e sem espaços nas pontas (índice único em lower(email)). */
    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusUsuario status;

    @Column(name = "ultimo_acesso")
    private Instant ultimoAcesso;

    public Usuario(String email, String senhaHash, Papel perfil) {
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.status = StatusUsuario.ATIVO;
    }

    public boolean isAtivo() {
        return status == StatusUsuario.ATIVO;
    }

    public void registrarAcesso() {
        this.ultimoAcesso = Instant.now();
    }

    public void alterarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }
}
