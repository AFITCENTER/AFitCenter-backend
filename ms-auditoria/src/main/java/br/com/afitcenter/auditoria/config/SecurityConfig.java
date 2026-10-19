package br.com.afitcenter.auditoria.config;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import br.com.afitcenter.core.web.seguranca.SegurancaPadrao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return SegurancaPadrao.configurar(http, filter, auth -> auth
                .requestMatchers("/v1/admin/**").hasAuthority(Papel.ADMIN.name())
                .requestMatchers("/v1/**").hasAuthority(Papel.NUTRICIONISTA.name()));
    }
}
