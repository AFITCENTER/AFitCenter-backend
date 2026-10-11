package br.com.afitcenter.gateway.filter;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FiltrosGatewayTest {

    private final JwtAuthFilter jwtAuthFilter =
            new JwtAuthFilter(new JwtVerificador(ChavesTeste.PUBLICA, ChavesTeste.ISSUER));

    @Test
    void jwtValidoSegueParaOServico() {
        var exchange = exchange(MockServerHttpRequest.get("/ms-paciente/v1/pacientes")
                .header(HttpHeaders.AUTHORIZATION, TokensTeste.nutricionista(UUID.randomUUID())));

        assertThat(executar(jwtAuthFilter::filter, exchange)).isNotNull();
    }

    @Test
    void semJwtResponde401() {
        var exchange = exchange(MockServerHttpRequest.get("/ms-paciente/v1/pacientes"));

        assertThat(executar(jwtAuthFilter::filter, exchange)).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rotaPublicaDescartaTokenInvalidoESegueAnonima() {
        var exchange = exchange(MockServerHttpRequest.post("/ms-auth/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer vencido"));

        ServerWebExchange encaminhada = executar(jwtAuthFilter.opcional()::filter, exchange);

        assertThat(encaminhada).isNotNull();
        assertThat(encaminhada.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void bloqueiaRotasInternasEActuatorDosServicos() {
        var filtro = new RotasInternasBloqueioFilter();
        for (String path : new String[]{"/ms-paciente/internal/pacientes/1", "/ms-paciente/actuator/env",
                "/ms-paciente//INTERNAL/x"}) {
            var exchange = exchange(MockServerHttpRequest.get(path));
            assertThat(executar(filtro::filter, exchange)).as(path).isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(executar(filtro::filter, exchange(MockServerHttpRequest.get("/ms-paciente/v1/pacientes"))))
                .isNotNull();
    }

    @Test
    void removeHeadersInternosVindosDeFora() {
        var exchange = exchange(MockServerHttpRequest.get("/ms-paciente/v1/pacientes")
                .header("X-Internal-Token", "forjado")
                .header("X-Afit-Qualquer", "x")
                .header("Accept", "application/json"));

        ServerWebExchange encaminhada = executar(new HeadersInternosSanitizerFilter()::filter, exchange);

        assertThat(encaminhada.getRequest().getHeaders().keySet()).containsExactly("Accept");
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    interface Filtro {
        Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);
    }

    /** Executa o filtro e devolve o exchange repassado à cadeia (null se o filtro respondeu sozinho). */
    private static ServerWebExchange executar(Filtro filtro, ServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> repassado = new AtomicReference<>();
        filtro.filter(exchange, ex -> {
            repassado.set(ex);
            return Mono.empty();
        }).block();
        return repassado.get();
    }
}
