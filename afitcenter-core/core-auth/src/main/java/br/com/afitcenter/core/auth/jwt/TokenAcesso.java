package br.com.afitcenter.core.auth.jwt;

import br.com.afitcenter.core.base.security.Papel;

import java.util.Set;
import java.util.UUID;

/**
 * Identidade extraída de um token de acesso já verificado.
 *
 * <p>Para o nutricionista, {@code usuarioId} também é o id do tenant: o perfil do
 * nutricionista é criado com o mesmo UUID do usuário.</p>
 */
public record TokenAcesso(UUID usuarioId, Set<Papel> papeis) {

    public TokenAcesso {
        papeis = Set.copyOf(papeis);
    }

    public boolean possui(Papel papel) {
        return papeis.contains(papel);
    }
}
