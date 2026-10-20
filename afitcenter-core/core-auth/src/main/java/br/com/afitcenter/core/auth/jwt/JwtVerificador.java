package br.com.afitcenter.core.auth.jwt;

import br.com.afitcenter.core.base.security.Papel;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.List;
import java.util.UUID;

/**
 * Valida tokens de acesso assinados pelo ms-pessoa (ES256).
 *
 * <p>Usa apenas a chave pública: quem verifica tokens não consegue emiti-los. Por isso o
 * Gateway e os microsserviços validam o JWT sem receber nenhum segredo capaz de forjar
 * identidades.</p>
 */
public class JwtVerificador {

    public static final String CLAIM_TIPO = "typ";
    public static final String TIPO_ACESSO = "access";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTVerifier verifier;

    public JwtVerificador(String chavePublica, String issuer) {
        if (issuer == null || issuer.isBlank())
            throw new IllegalStateException("O issuer do JWT precisa ser configurado.");
        Algorithm algoritmo = Algorithm.ECDSA256(ChavesJwt.chavePublica(chavePublica), null);
        this.verifier = JWT.require(algoritmo)
                .withIssuer(issuer)
                .withClaim(CLAIM_TIPO, TIPO_ACESSO)
                .build();
    }

    /** Valida o header {@code Authorization: Bearer <token>} e devolve a identidade. */
    public TokenAcesso verificar(String authorizationHeader) {
        DecodedJWT jwt;
        try {
            jwt = verifier.verify(extrairToken(authorizationHeader));
        } catch (JWTVerificationException exception) {
            throw new TokenInvalidoException("Token inválido ou expirado.", exception);
        }

        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new TokenInvalidoException("Token sem identificador de usuário válido.");
        }

        List<String> roles = jwt.getClaim(Papel.CLAIM_ROLES).asList(String.class);
        return new TokenAcesso(usuarioId, Papel.parse(roles));
    }

    private String extrairToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank())
            throw new TokenInvalidoException("Header Authorization não informado.");

        if (!authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length()))
            throw new TokenInvalidoException("Header Authorization deve utilizar o formato Bearer.");

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank())
            throw new TokenInvalidoException("Token JWT não informado.");
        return token;
    }
}
