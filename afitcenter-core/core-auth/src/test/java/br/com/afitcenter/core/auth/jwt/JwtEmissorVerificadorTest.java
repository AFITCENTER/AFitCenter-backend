package br.com.afitcenter.core.auth.jwt;

import br.com.afitcenter.core.base.security.Papel;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtEmissorVerificadorTest {

    private static final String ISSUER = "afitcenter-teste";

    private final KeyPair par = gerarPar();
    private final String privada = Base64.getEncoder().encodeToString(par.getPrivate().getEncoded());
    private final String publica = Base64.getEncoder().encodeToString(par.getPublic().getEncoded());

    @Test
    void tokenEmitidoEValidadoComAChavePublica() {
        UUID usuario = UUID.randomUUID();
        String token = new JwtEmissor(privada, publica, ISSUER, Duration.ofMinutes(15))
                .emitir(usuario, Set.of(Papel.NUTRICIONISTA));

        TokenAcesso acesso = new JwtVerificador(publica, ISSUER).verificar("Bearer " + token);

        assertThat(acesso.usuarioId()).isEqualTo(usuario);
        assertThat(acesso.papeis()).containsExactly(Papel.NUTRICIONISTA);
    }

    @Test
    void recusaTokenAssinadoPorOutraChave() {
        KeyPair outro = gerarPar();
        String token = new JwtEmissor(
                Base64.getEncoder().encodeToString(outro.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(outro.getPublic().getEncoded()),
                ISSUER, Duration.ofMinutes(15)
        ).emitir(UUID.randomUUID(), Set.of(Papel.ADMIN));

        assertThatThrownBy(() -> new JwtVerificador(publica, ISSUER).verificar("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void recusaTokenExpiradoEOutroIssuer() {
        Clock passado = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expirado = new JwtEmissor(privada, publica, ISSUER, Duration.ofMinutes(15), passado)
                .emitir(UUID.randomUUID(), Set.of(Papel.NUTRICIONISTA));
        String outroIssuer = new JwtEmissor(privada, publica, "outro", Duration.ofMinutes(15))
                .emitir(UUID.randomUUID(), Set.of(Papel.NUTRICIONISTA));

        JwtVerificador verificador = new JwtVerificador(publica, ISSUER);
        assertThatThrownBy(() -> verificador.verificar("Bearer " + expirado)).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> verificador.verificar("Bearer " + outroIssuer)).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> verificador.verificar("Basic abc")).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void falhaNaInicializacaoComChavesQueNaoFormamPar() {
        String publicaDeOutroPar = Base64.getEncoder().encodeToString(gerarPar().getPublic().getEncoded());
        assertThatThrownBy(() -> new JwtEmissor(privada, publicaDeOutroPar, ISSUER, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static KeyPair gerarPar() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(new ECGenParameterSpec("secp256r1"));
            return gerador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
