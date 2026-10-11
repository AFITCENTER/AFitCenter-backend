package br.com.afitcenter.core.mensageria.autoconfigure;

import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.outbox.Outbox;
import br.com.afitcenter.core.mensageria.outbox.OutboxPublicador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exchanges, conversor JSON e outbox.
 *
 * <ul>
 *     <li>Sempre (com RabbitMQ no classpath): exchange de eventos, DLX e conversor JSON, usado tanto
 *     pelo {@code RabbitTemplate} quanto pelos {@code @RabbitListener}.</li>
 *     <li>{@code afitcenter.outbox.habilitado=true}: bean {@link Outbox} (serviços produtores).</li>
 *     <li>{@code afitcenter.outbox.publicador-habilitado} (padrão {@code true}): agenda o
 *     {@link OutboxPublicador}. Os testes desligam para conferir os eventos na tabela.</li>
 * </ul>
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, JdbcTemplateAutoConfiguration.class},
        before = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class MensageriaAutoConfiguration {

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(Eventos.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange eventosDeadLetterExchange() {
        return new DirectExchange(Eventos.DLX, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter mensagemJsonConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(name = "afitcenter.outbox.habilitado", havingValue = "true")
    static class OutboxConfiguration {

        @Bean
        Outbox outbox(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new Outbox(jdbcTemplate, objectMapper);
        }

        @Configuration(proxyBeanMethods = false)
        @EnableScheduling
        @ConditionalOnProperty(name = "afitcenter.outbox.publicador-habilitado", havingValue = "true", matchIfMissing = true)
        static class PublicadorConfiguration {

            @Bean
            OutboxPublicador outboxPublicador(
                    JdbcTemplate jdbcTemplate,
                    RabbitTemplate rabbitTemplate,
                    PlatformTransactionManager transactionManager,
                    @Value("${afitcenter.outbox.max-tentativas:10}") int maxTentativas,
                    @Value("${afitcenter.outbox.timeout-confirmacao-ms:5000}") long timeoutConfirmacaoMs
            ) {
                return new OutboxPublicador(jdbcTemplate, rabbitTemplate, new TransactionTemplate(transactionManager),
                        maxTentativas, timeoutConfirmacaoMs);
            }
        }
    }
}
