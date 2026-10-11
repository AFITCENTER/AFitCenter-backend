package br.com.afitcenter.gateway.filter;

import br.com.afitcenter.gateway.erro.RespostaErro;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Impede que clientes externos alcancem {@code /internal/**} e o Actuator dos microsserviços.
 * Responde 404 para não confirmar que a rota existe.
 */
@Component
public class RotasInternasBloqueioFilter implements GlobalFilter, Ordered {

    /** {@code /<servico>/internal/...} ou {@code /<servico>/actuator/...}, caminho já decodificado. */
    private static final Pattern ROTA_INTERNA =
            Pattern.compile("^/+[^/]+/+(internal|actuator)(/.*)?$", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && ROTA_INTERNA.matcher(path).matches())
            return RespostaErro.escrever(exchange, HttpStatus.NOT_FOUND, "Endpoint não encontrado.");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
