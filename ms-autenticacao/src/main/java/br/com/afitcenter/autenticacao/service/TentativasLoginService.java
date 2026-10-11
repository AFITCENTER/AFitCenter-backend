package br.com.afitcenter.autenticacao.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de login com senha incorreta por e-mail.
 *
 * <p>Após {@code maxFalhas} erros dentro da janela, o e-mail fica bloqueado até a janela expirar.
 * O controle é em memória: com mais de uma instância do serviço, cada uma tem o próprio contador
 * (para um limite global, use Redis).</p>
 */
@Component
public class TentativasLoginService {

    private static final int LIMITE_REGISTROS_ANTES_DA_LIMPEZA = 10_000;

    private final int maxFalhas;
    private final Duration janela;
    private final Clock clock;
    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    private record Registro(int falhas, Instant inicio) {
    }

    @Autowired
    public TentativasLoginService(@Value("${afitcenter.login.max-falhas:5}") int maxFalhas,
                                  @Value("${afitcenter.login.bloqueio-minutos:15}") long bloqueioMinutos) {
        this(maxFalhas, Duration.ofMinutes(bloqueioMinutos), Clock.systemUTC());
    }

    TentativasLoginService(int maxFalhas, Duration janela, Clock clock) {
        this.maxFalhas = maxFalhas;
        this.janela = janela;
        this.clock = clock;
    }

    public void verificarBloqueio(String email) {
        Registro registro = registros.get(chave(email));
        if (registro != null && !expirado(registro) && registro.falhas() >= maxFalhas)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente novamente em alguns minutos.");
    }

    public void registrarFalha(String email) {
        if (registros.size() >= LIMITE_REGISTROS_ANTES_DA_LIMPEZA)
            registros.values().removeIf(this::expirado);
        registros.compute(chave(email), (k, atual) -> atual == null || expirado(atual)
                ? new Registro(1, clock.instant())
                : new Registro(atual.falhas() + 1, atual.inicio()));
    }

    public void registrarSucesso(String email) {
        registros.remove(chave(email));
    }

    private boolean expirado(Registro registro) {
        return !clock.instant().isBefore(registro.inicio().plus(janela));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
