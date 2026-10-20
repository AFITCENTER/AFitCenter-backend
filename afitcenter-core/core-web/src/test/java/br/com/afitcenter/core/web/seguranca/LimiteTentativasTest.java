package br.com.afitcenter.core.web.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteTentativasTest {

    private final Relogio relogio = new Relogio();
    private final LimiteTentativas limite =
            new LimiteTentativas(3, Duration.ofMinutes(15), "Muitas tentativas.", relogio);

    @Test
    void bloqueiaAoAtingirOLimiteAteAJanelaExpirar() {
        for (int i = 0; i < 3; i++) {
            limite.verificarBloqueio("ana");
            limite.registrarFalha("ana");
        }
        assertThatThrownBy(() -> limite.verificarBloqueio("ana"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Muitas tentativas.")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThatCode(() -> limite.verificarBloqueio("bruno")).as("outra chave segue livre").doesNotThrowAnyException();

        relogio.avancar(Duration.ofMinutes(15));
        assertThatCode(() -> limite.verificarBloqueio("ana")).doesNotThrowAnyException();
    }

    @Test
    void sucessoZeraAsFalhas() {
        limite.registrarFalha("ana");
        limite.registrarFalha("ana");
        limite.registrarSucesso("ana");
        limite.registrarFalha("ana");
        limite.registrarFalha("ana");

        assertThatCode(() -> limite.verificarBloqueio("ana")).doesNotThrowAnyException();
    }

    @Test
    void falhaDepoisDaJanelaRecomecaAContagem() {
        limite.registrarFalha("ana");
        limite.registrarFalha("ana");
        relogio.avancar(Duration.ofMinutes(16));
        limite.registrarFalha("ana");
        limite.registrarFalha("ana");

        assertThatCode(() -> limite.verificarBloqueio("ana")).doesNotThrowAnyException();
    }

    private static final class Relogio extends Clock {

        private Instant agora = Instant.parse("2026-01-01T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
