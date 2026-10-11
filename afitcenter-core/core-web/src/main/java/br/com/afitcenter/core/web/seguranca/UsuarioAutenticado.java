package br.com.afitcenter.core.web.seguranca;

import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.base.security.Papel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Usuário e tenant da requisição atual, preenchidos pelo {@link AutenticacaoRequisicaoFilter}.
 *
 * <p>Regra de isolamento: todo acesso a dado de negócio filtra por {@link #nutricionistaId()}.
 * Recurso de outro nutricionista responde 404 (não 403), para não revelar que ele existe.</p>
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    /** Token da requisição; 401 se a chamada não for de um usuário (anônima ou interna). */
    public static TokenAcesso token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TokenAcesso token)
            return token;
        throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
    }

    public static UUID usuarioId() {
        return token().usuarioId();
    }

    public static boolean possui(Papel papel) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof TokenAcesso token && token.possui(papel);
    }

    /**
     * Id do nutricionista dono dos dados (tenant). É o próprio id do usuário, que precisa ter o
     * papel {@code NUTRICIONISTA}; caso contrário responde 403.
     */
    public static UUID nutricionistaId() {
        TokenAcesso token = token();
        if (!token.possui(Papel.NUTRICIONISTA))
            throw new AccessDeniedException("Recurso disponível apenas para nutricionistas.");
        return token.usuarioId();
    }
}
