package br.com.afitcenter.autenticacao.config;

import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import br.com.afitcenter.core.web.seguranca.SegurancaPadrao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return SegurancaPadrao.configurar(http, filter, auth -> auth
                .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
