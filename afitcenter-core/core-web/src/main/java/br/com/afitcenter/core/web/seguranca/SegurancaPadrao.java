package br.com.afitcenter.core.web.seguranca;

import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Segurança padrão dos microsserviços: API stateless, sem sessão, CSRF, form login ou HTTP Basic,
 * com erros em Problem Details.
 *
 * <p>Públicos: health/info do Actuator, documentação OpenAPI e {@code /error}. {@code /internal/**} e
 * o restante do Actuator exigem {@code INTERNAL}. Todo o resto exige um usuário autenticado.</p>
 *
 * <pre>
 * &#64;Bean
 * SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
 *     return SegurancaPadrao.configurar(http, filter, auth -> auth
 *             .requestMatchers(HttpMethod.POST, "/v1/cadastros").permitAll()
 *             .requestMatchers("/v1/**").hasAuthority("NUTRICIONISTA"));
 * }
 * </pre>
 */
public final class SegurancaPadrao {

    public static final String[] CAMINHOS_PUBLICOS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/error"
    };

    private SegurancaPadrao() {
    }

    public static SecurityFilterChain configurar(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return configurar(http, filter, Customizer.withDefaults());
    }

    /** @param regrasAdicionais aplicadas antes da regra final, que exige usuário autenticado */
    public static SecurityFilterChain configurar(
            HttpSecurity http,
            AutenticacaoRequisicaoFilter filter,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> regrasAdicionais
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> RespostaProblema.escrever(
                                request, response, HttpStatus.UNAUTHORIZED.value(), "Autenticação necessária."))
                        .accessDeniedHandler((request, response, e) -> RespostaProblema.escrever(
                                request, response, HttpStatus.FORBIDDEN.value(), "Acesso negado a este recurso.")))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(CAMINHOS_PUBLICOS).permitAll();
                    auth.requestMatchers("/internal/**", "/actuator/**")
                            .hasAuthority(AutenticacaoRequisicaoFilter.AUTHORITY_INTERNAL);
                    regrasAdicionais.customize(auth);
                    auth.anyRequest().hasAuthority(AutenticacaoRequisicaoFilter.AUTHORITY_USER);
                })
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
