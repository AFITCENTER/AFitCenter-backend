package br.com.afitcenter.core.auth.jwt;

import br.com.afitcenter.core.base.security.Papel;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Emite tokens de acesso ES256. Deve existir apenas no ms-autenticacao, único serviço
 * que recebe a chave privada (e nos testes, com o par de chaves de teste).
 */
public class JwtEmissor {

    private final Algorithm algoritmo;
    private final String issuer;
    private final Duration validade;
    private final Clock clock;

    public JwtEmissor(String chavePrivada, String chavePublica, String issuer, Duration validade) {
        this(chavePrivada, chavePublica, issuer, validade, Clock.systemUTC());
    }

    public JwtEmissor(String chavePrivada, String chavePublica, String issuer, Duration validade, Clock clock) {
        if (issuer == null || issuer.isBlank())
            throw new IllegalStateException("O issuer do JWT precisa ser configurado.");
        if (validade == null || validade.isNegative() || validade.isZero())
            throw new IllegalStateException("A validade do token de acesso precisa ser positiva.");
        this.algoritmo = Algorithm.ECDSA256(ChavesJwt.chavePublica(chavePublica), ChavesJwt.chavePrivada(chavePrivada));
        this.issuer = issuer;
        this.validade = validade;
        this.clock = clock;
        validarPar();
    }

    public String emitir(UUID usuarioId, Collection<Papel> papeis) {
        if (usuarioId == null)
            throw new IllegalArgumentException("Usuário inválido para geração do token.");

        Instant agora = clock.instant();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(usuarioId.toString())
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(agora)
                .withExpiresAt(agora.plus(validade))
                .withClaim(Papel.CLAIM_ROLES, Papel.nomes(papeis))
                .withClaim(JwtVerificador.CLAIM_TIPO, JwtVerificador.TIPO_ACESSO)
                .sign(algoritmo);
    }

    public Duration validade() {
        return validade;
    }

    /** Falha na inicialização se a chave pública não corresponder à privada. */
    private void validarPar() {
        String teste = JWT.create().withIssuer(issuer).sign(algoritmo);
        try {
            JWT.require(algoritmo).withIssuer(issuer).build().verify(teste);
        } catch (JWTVerificationException exception) {
            throw new IllegalStateException("As chaves pública e privada do JWT não formam um par.", exception);
        }
    }
}
