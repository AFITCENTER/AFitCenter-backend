package br.com.afitcenter.gateway.config;

import br.com.afitcenter.gateway.filter.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Rotas do Gateway. O primeiro segmento do caminho identifica o serviço e é removido
 * ({@code /ms-paciente/v1/pacientes} → {@code ms-paciente /v1/pacientes}).
 *
 * <p>Rotas públicas usam {@code order(-1)} para serem avaliadas antes da rota protegida do mesmo
 * serviço e descartam tokens inválidos ({@code opcional()}). Todo o resto exige JWT válido.</p>
 */
@Configuration
public class GatewayConfig {

    private static final int ROTA_PUBLICA = -1;

    /** Prefixo no Gateway → nome do serviço no Eureka. */
    private static final List<String[]> SERVICOS_PROTEGIDOS = List.of(
            new String[]{"ms-nutricionista", "ms-nutricionista"},
            new String[]{"ms-paciente", "ms-paciente"},
            new String[]{"ms-consulta", "ms-consulta"},
            new String[]{"ms-avaliacao", "ms-avaliacao"},
            new String[]{"ms-dieta", "ms-dieta"},
            new String[]{"ms-planos", "ms-planos"},
            new String[]{"ms-pagamentos", "ms-pagamentos"},
            new String[]{"ms-auditoria", "ms-auditoria"}
    );

    @Bean
    public RouteLocator rotas(RouteLocatorBuilder builder, JwtAuthFilter jwtAuthFilter,
                              @Value("${afitcenter.docs.habilitado:false}") boolean docsHabilitado) {
        var rotas = builder.routes()

                // Login, cadastro do paciente e conta: o ms-pessoa decide o que é público (login e cadastro) e o que
                // exige JWT (conta).
                .route("ms-pessoa", r -> r
                        .path("/ms-pessoa/**")
                        .filters(f -> f.stripPrefix(1).filter(jwtAuthFilter.opcional()))
                        .uri("lb://ms-pessoa"))

                // Cadastro do nutricionista (público).
                .route("ms-nutricionista-cadastro", r -> r
                        .order(ROTA_PUBLICA)
                        .path("/ms-nutricionista/v1/cadastros")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f.stripPrefix(1).filter(jwtAuthFilter.opcional()))
                        .uri("lb://ms-nutricionista"))

                // Catálogo de planos (página de preços, público).
                .route("ms-planos-catalogo", r -> r
                        .order(ROTA_PUBLICA)
                        .path("/ms-planos/v1/planos", "/ms-planos/v1/planos/*")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f.stripPrefix(1).filter(jwtAuthFilter.opcional()))
                        .uri("lb://ms-planos"))

                // Webhooks dos provedores de pagamento: sem JWT, o ms-pagamentos confere a assinatura de cada um.
                .route("ms-pagamentos-webhooks", r -> r
                        .order(ROTA_PUBLICA)
                        .path("/ms-pagamentos/v1/webhooks/*")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f.stripPrefix(1).filter(jwtAuthFilter.opcional()))
                        .uri("lb://ms-pagamentos"));

        if (docsHabilitado) {
            for (String[] servico : SERVICOS_PROTEGIDOS) {
                rotas.route(servico[0] + "-docs", r -> r
                        .order(ROTA_PUBLICA)
                        .path("/" + servico[0] + "/v3/api-docs/**", "/" + servico[0] + "/v3/api-docs",
                                "/" + servico[0] + "/swagger-ui/**", "/" + servico[0] + "/swagger-ui.html")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://" + servico[1]));
            }
        }

        for (String[] servico : SERVICOS_PROTEGIDOS) {
            rotas.route(servico[0], r -> r
                    .path("/" + servico[0] + "/**")
                    .filters(f -> f.stripPrefix(1).filter(jwtAuthFilter))
                    .uri("lb://" + servico[1]));
        }

        return rotas.build();
    }
}
