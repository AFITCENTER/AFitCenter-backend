package br.com.afitcenter.core.base.http;

import java.util.Locale;

/**
 * Headers reservados à comunicação interna.
 *
 * <p>A identidade do usuário trafega somente no JWT, validado por cada serviço. O Gateway
 * descarta qualquer header reservado vindo de fora para que nenhum cliente se passe por
 * uma chamada interna.</p>
 */
public final class InternalHeaders {

    /** Token compartilhado apenas entre os microsserviços (o Gateway não o possui). */
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    /** Prefixo reservado para headers internos futuros. */
    public static final String PREFIXO_RESERVADO = "X-Afit-";

    private InternalHeaders() {
    }

    /** Indica se o header deve ser removido quando vier de fora da rede interna. */
    public static boolean isReservado(String nomeHeader) {
        if (nomeHeader == null) return false;
        String nome = nomeHeader.toLowerCase(Locale.ROOT);
        return nome.equals(INTERNAL_TOKEN.toLowerCase(Locale.ROOT))
                || nome.startsWith(PREFIXO_RESERVADO.toLowerCase(Locale.ROOT));
    }
}
