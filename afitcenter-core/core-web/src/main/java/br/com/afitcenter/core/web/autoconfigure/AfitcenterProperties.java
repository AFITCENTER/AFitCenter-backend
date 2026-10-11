package br.com.afitcenter.core.web.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades comuns de segurança.
 *
 * <pre>
 * afitcenter.jwt.chave-publica=${AFIT_JWT_PUBLIC_KEY}
 * afitcenter.jwt.issuer=${AFIT_JWT_ISSUER}
 * afitcenter.seguranca.token-interno=${AFIT_INTERNAL_TOKEN}
 * </pre>
 */
@ConfigurationProperties(prefix = "afitcenter")
public record AfitcenterProperties(Jwt jwt, Seguranca seguranca) {

    public record Jwt(String chavePublica, String issuer) {
    }

    public record Seguranca(String tokenInterno) {
    }

    public String chavePublica() {
        return jwt == null ? null : jwt.chavePublica();
    }

    public String issuer() {
        return jwt == null ? null : jwt.issuer();
    }

    public String tokenInterno() {
        return seguranca == null ? null : seguranca.tokenInterno();
    }
}
