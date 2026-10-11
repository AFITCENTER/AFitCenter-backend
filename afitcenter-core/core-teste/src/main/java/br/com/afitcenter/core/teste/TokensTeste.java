package br.com.afitcenter.core.teste;

import br.com.afitcenter.core.auth.jwt.JwtEmissor;
import br.com.afitcenter.core.base.security.Papel;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Emite headers {@code Authorization} válidos para os testes. */
public final class TokensTeste {

    private static final JwtEmissor EMISSOR =
            new JwtEmissor(ChavesTeste.PRIVADA, ChavesTeste.PUBLICA, ChavesTeste.ISSUER, Duration.ofMinutes(15));

    private TokensTeste() {
    }

    /** {@code Bearer <jwt>} de um nutricionista; o id também é o tenant. */
    public static String nutricionista(UUID nutricionistaId) {
        return comPapeis(nutricionistaId, Papel.NUTRICIONISTA);
    }

    public static String comPapeis(UUID usuarioId, Papel... papeis) {
        return "Bearer " + EMISSOR.emitir(usuarioId, Set.of(papeis));
    }
}
