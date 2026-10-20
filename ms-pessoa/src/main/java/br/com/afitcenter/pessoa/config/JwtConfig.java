package br.com.afitcenter.pessoa.config;

import br.com.afitcenter.core.auth.jwt.JwtEmissor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class JwtConfig {

    /** Único ponto do ecossistema com a chave privada: só este serviço emite tokens. */
    @Bean
    JwtEmissor jwtEmissor(
            @Value("${afitcenter.jwt.chave-privada}") String chavePrivada,
            @Value("${afitcenter.jwt.chave-publica}") String chavePublica,
            @Value("${afitcenter.jwt.issuer}") String issuer,
            @Value("${afitcenter.jwt.validade-minutos:15}") long validadeMinutos
    ) {
        return new JwtEmissor(chavePrivada, chavePublica, issuer, Duration.ofMinutes(validadeMinutos));
    }
}
