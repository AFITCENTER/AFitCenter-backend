package br.com.afitcenter.core.auditoria;

import br.com.afitcenter.core.mensageria.autoconfigure.MensageriaAutoConfiguration;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Liga a trilha de auditoria num microsserviço. Basta a dependência {@code core-auditoria} e a outbox
 * habilitada (tabela {@code outbox_evento} na migração):
 *
 * <pre>
 * afitcenter.outbox.habilitado=true
 * # opcional: registra também as leituras (serviços com prontuário), menos as de catálogo
 * afitcenter.auditoria.leituras=true
 * afitcenter.auditoria.leituras-ignoradas=/v1/alimentos/**,/v1/nutrientes
 * </pre>
 *
 * <p>{@code afitcenter.auditoria.habilitado=false} desliga tudo.</p>
 */
@AutoConfiguration(after = MensageriaAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "afitcenter.auditoria.habilitado", havingValue = "true", matchIfMissing = true)
public class AuditoriaAutoConfiguration {

    @Bean
    public Auditoria auditoria(ObjectProvider<Outbox> outbox, PlatformTransactionManager transactionManager,
                               @Value("${spring.application.name}") String servico) {
        Outbox disponivel = outbox.getIfAvailable();
        if (disponivel == null)
            throw new IllegalStateException("A auditoria grava na outbox: defina afitcenter.outbox.habilitado=true e "
                    + "crie a tabela outbox_evento na migração (ou desligue com afitcenter.auditoria.habilitado=false).");
        return new Auditoria(disponivel, transactionManager, servico);
    }

    @Bean
    public AuditoriaRespostaAdvice auditoriaRespostaAdvice() {
        return new AuditoriaRespostaAdvice();
    }

    @Bean
    public WebMvcConfigurer auditoriaWebMvcConfigurer(
            Auditoria auditoria,
            @Value("${afitcenter.auditoria.leituras:false}") boolean leituras,
            @Value("${afitcenter.auditoria.leituras-ignoradas:}") List<String> leiturasIgnoradas) {
        AuditoriaInterceptor interceptor = new AuditoriaInterceptor(auditoria, leituras, leiturasIgnoradas);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(@NonNull InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/v1/**");
            }
        };
    }
}
