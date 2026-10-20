package br.com.afitcenter.paciente.config;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.core.web.seguranca.SegurancaPadrao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class SecurityConfig {

    /** O portal é do paciente; todo o resto, do nutricionista. */
    @Bean
    SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return SegurancaPadrao.configurar(http, filter, auth -> auth
                .requestMatchers("/v1/portal/**").hasAuthority(Papel.PACIENTE.name())
                .requestMatchers("/v1/**").hasAuthority(Papel.NUTRICIONISTA.name()));
    }

    /** Códigos de acesso errados por conta, no portal. */
    @Bean
    LimiteTentativas tentativasCodigoAcesso(@Value("${afitcenter.portal.max-falhas:5}") int maxFalhas,
                                            @Value("${afitcenter.portal.bloqueio-minutos:15}") long bloqueioMinutos) {
        return new LimiteTentativas(maxFalhas, Duration.ofMinutes(bloqueioMinutos),
                "Muitos códigos de acesso incorretos. Tente novamente em alguns minutos.");
    }
}
