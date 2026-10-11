package br.com.afitcenter.gateway.filter;

import br.com.afitcenter.core.base.http.InternalHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/** Remove headers reservados à comunicação interna (ex.: {@code X-Internal-Token}) vindos de fora. */
@Component
public class HeadersInternosSanitizerFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest().mutate()
                .headers(HeadersInternosSanitizerFilter::removerReservados)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    static void removerReservados(HttpHeaders headers) {
        List<String> reservados = headers.keySet().stream().filter(InternalHeaders::isReservado).toList();
        reservados.forEach(headers::remove);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
