package br.com.afitcenter.planos.config;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import br.com.afitcenter.core.web.seguranca.SegurancaPadrao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /** Catálogo público (página de preços); manutenção dos planos só pelo administrador. */
    @Bean
    SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return SegurancaPadrao.configurar(http, filter, auth -> auth
                .requestMatchers(HttpMethod.GET, "/v1/planos", "/v1/planos/*").permitAll()
                .requestMatchers("/v1/admin/**").hasAuthority(Papel.ADMIN.name())
                .requestMatchers("/v1/**").hasAuthority(Papel.NUTRICIONISTA.name()));
    }
}
