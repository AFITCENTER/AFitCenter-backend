package br.com.afitcenter.pessoa.config;

import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import br.com.afitcenter.core.web.seguranca.LimiteTentativas;
import br.com.afitcenter.core.web.seguranca.SegurancaPadrao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class SecurityConfig {

    /**
     * Públicos: login, cadastro do paciente, recuperação de senha e confirmação do e-mail (o token do link
     * identifica a conta). O resto da conta, inclusive o reenvio da confirmação, exige JWT de qualquer papel.
     */
    @Bean
    SecurityFilterChain filtrosSeguranca(HttpSecurity http, AutenticacaoRequisicaoFilter filter) throws Exception {
        return SegurancaPadrao.configurar(http, filter, auth -> auth
                .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/cadastros/paciente",
                        "/v1/senha/esqueci", "/v1/senha/redefinir", "/v1/conta/email/confirmacao").permitAll());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Senhas erradas por e-mail. Vale para o login e para o cadastro numa conta que já existe (que também confere
     * a senha), para nenhum dos dois servir de atalho a quem tenta adivinhar senhas.
     */
    @Bean
    LimiteTentativas tentativasSenha(@Value("${afitcenter.login.max-falhas:5}") int maxFalhas,
                                     @Value("${afitcenter.login.bloqueio-minutos:15}") long bloqueioMinutos) {
        return new LimiteTentativas(maxFalhas, Duration.ofMinutes(bloqueioMinutos),
                "Muitas tentativas com senha incorreta. Tente novamente em alguns minutos.");
    }

    /**
     * E-mails com link pedidos por endereço: "esqueci a senha" e reenvio da confirmação. Todo pedido conta, exista
     * ou não a conta, para o 429 não revelar quais e-mails estão cadastrados e ninguém lotar a caixa de outra
     * pessoa.
     */
    @Bean
    LimiteTentativas enviosEmail(@Value("${afitcenter.email.max-envios:5}") int maxEnvios,
                                 @Value("${afitcenter.email.janela-minutos:60}") long janelaMinutos) {
        return new LimiteTentativas(maxEnvios, Duration.ofMinutes(janelaMinutos),
                "Muitos pedidos de e-mail para este endereço. Tente novamente mais tarde.");
    }
}
