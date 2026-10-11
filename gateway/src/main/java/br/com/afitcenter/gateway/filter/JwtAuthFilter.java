package br.com.afitcenter.gateway.filter;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.auth.jwt.TokenInvalidoException;
import br.com.afitcenter.gateway.erro.RespostaErro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejeita no Gateway requisições sem JWT válido.
 *
 * <p>O token segue intacto no header {@code Authorization} e cada microsserviço o valida de novo.
 * O Gateway não possui nenhum segredo: não emite tokens nem conhece o token interno.</p>
 */
@Slf4j
@Component
public class JwtAuthFilter implements GatewayFilter {

    private final JwtVerificador jwtVerificador;

    @Autowired
    public JwtAuthFilter(@Value("${afitcenter.jwt.chave-publica}") String chavePublica,
                         @Value("${afitcenter.jwt.issuer}") String issuer) {
        this(new JwtVerificador(chavePublica, issuer));
    }

    JwtAuthFilter(JwtVerificador jwtVerificador) {
        this.jwtVerificador = jwtVerificador;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            jwtVerificador.verificar(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return chain.filter(exchange);
        } catch (TokenInvalidoException e) {
            log.debug("Requisição rejeitada pelo Gateway: {}", e.getMessage());
            return RespostaErro.escrever(exchange, HttpStatus.UNAUTHORIZED, "Token ausente, inválido ou expirado.");
        }
    }

    /**
     * Para rotas públicas: mantém o token quando é válido e, caso contrário, remove o header para o
     * serviço tratar a chamada como anônima (um token vencido não pode impedir um login).
     */
    public GatewayFilter opcional() {
        return (exchange, chain) -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || authorization.isBlank())
                return chain.filter(exchange);
            try {
                jwtVerificador.verificar(authorization);
                return chain.filter(exchange);
            } catch (TokenInvalidoException e) {
                var anonima = exchange.getRequest().mutate()
                        .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                        .build();
                return chain.filter(exchange.mutate().request(anonima).build());
            }
        };
    }
}
