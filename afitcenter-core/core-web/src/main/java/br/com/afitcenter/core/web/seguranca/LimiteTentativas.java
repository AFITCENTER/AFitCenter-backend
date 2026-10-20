package br.com.afitcenter.core.web.seguranca;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas com erro por chave: senha errada por e-mail no login, código de acesso errado por pessoa
 * no portal do paciente.
 *
 * <p>Após {@code maxFalhas} erros dentro da janela, a chave fica bloqueada (429) até a janela expirar.
 * O controle é em memória: com mais de uma instância do serviço, cada uma tem o próprio contador
 * (para um limite global, use Redis).</p>
 */
public class LimiteTentativas {

    private static final int LIMITE_REGISTROS_ANTES_DA_LIMPEZA = 10_000;

    private final int maxFalhas;
    private final Duration janela;
    private final String mensagemBloqueio;
    private final Clock clock;
    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();

    private record Registro(int falhas, Instant inicio) {
    }

    public LimiteTentativas(int maxFalhas, Duration janela, String mensagemBloqueio) {
        this(maxFalhas, janela, mensagemBloqueio, Clock.systemUTC());
    }

    public LimiteTentativas(int maxFalhas, Duration janela, String mensagemBloqueio, Clock clock) {
        if (maxFalhas < 1)
            throw new IllegalStateException("O número máximo de falhas precisa ser positivo.");
        if (janela == null || janela.isNegative() || janela.isZero())
            throw new IllegalStateException("A janela de bloqueio precisa ser positiva.");
        this.maxFalhas = maxFalhas;
        this.janela = janela;
        this.mensagemBloqueio = mensagemBloqueio;
        this.clock = clock;
    }

    /** 429 se a chave atingiu o limite de falhas e a janela ainda não expirou. */
    public void verificarBloqueio(String chave) {
        Registro registro = registros.get(normalizar(chave));
        if (registro != null && !expirado(registro) && registro.falhas() >= maxFalhas)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, mensagemBloqueio);
    }

    public void registrarFalha(String chave) {
        if (registros.size() >= LIMITE_REGISTROS_ANTES_DA_LIMPEZA)
            registros.values().removeIf(this::expirado);
        registros.compute(normalizar(chave), (k, atual) -> atual == null || expirado(atual)
                ? new Registro(1, clock.instant())
                : new Registro(atual.falhas() + 1, atual.inicio()));
    }

    public void registrarSucesso(String chave) {
        registros.remove(normalizar(chave));
    }

    private boolean expirado(Registro registro) {
        return !clock.instant().isBefore(registro.inicio().plus(janela));
    }

    private static String normalizar(String chave) {
        return chave == null ? "" : chave;
    }
}
