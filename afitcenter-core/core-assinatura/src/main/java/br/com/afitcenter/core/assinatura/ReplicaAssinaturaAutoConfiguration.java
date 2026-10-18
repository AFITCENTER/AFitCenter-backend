package br.com.afitcenter.core.assinatura;

import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.autoconfigure.MensageriaAutoConfiguration;
import br.com.afitcenter.core.mensageria.fila.Filas;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liga a réplica da assinatura num serviço que aplica limites do plano. Basta a dependência
 * {@code core-assinatura}, a tabela {@code assinatura_ref} na migração e a propriedade com o nome da fila:
 *
 * <pre>
 * afitcenter.replica-assinatura.fila=ms-paciente.assinatura
 * </pre>
 */
@AutoConfiguration(after = {MensageriaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnProperty(name = "afitcenter.replica-assinatura.fila")
@EnableFeignClients(clients = AssinaturaInternoClient.class)
public class ReplicaAssinaturaAutoConfiguration {

    @Bean
    public AssinaturaRefRepository assinaturaRefRepository(JdbcTemplate jdbcTemplate) {
        return new AssinaturaRefRepository(jdbcTemplate);
    }

    @Bean
    public AssinaturaRefService assinaturaRefService(AssinaturaRefRepository repository, AssinaturaInternoClient client,
                                                     PlatformTransactionManager transactionManager) {
        TransactionTemplate propria = new TransactionTemplate(transactionManager);
        propria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new AssinaturaRefService(repository, client, propria);
    }

    @Bean
    public AssinaturaEventosListener assinaturaEventosListener(AssinaturaRefService service) {
        return new AssinaturaEventosListener(service);
    }

    @Bean
    public Declarables filaReplicaAssinatura(@Value("${afitcenter.replica-assinatura.fila}") String fila) {
        return Filas.declarar(fila, Eventos.PADRAO_ASSINATURA);
    }
}
