package br.com.afitcenter.core.teste;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sobe o microsserviço inteiro com PostgreSQL embutido (binários reais, sem Docker), migrações
 * Flyway e validação do mapeamento JPA ({@code ddl-auto=validate}).
 *
 * <p>Eureka e consumidores RabbitMQ ficam desligados e o publicador da outbox não roda: os eventos
 * ficam na tabela {@code outbox_evento}, onde o teste pode conferi-los. Clientes Feign devem ser
 * substituídos com {@code @MockitoBean}.</p>
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "afitcenter.jwt.chave-publica=" + ChavesTeste.PUBLICA,
        "afitcenter.jwt.issuer=" + ChavesTeste.ISSUER,
        "afitcenter.seguranca.token-interno=" + ChavesTeste.TOKEN_INTERNO,
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "afitcenter.outbox.publicador-habilitado=false",
        "spring.flyway.locations=classpath:db/migration/common"
})
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public @interface TesteIntegracao {
}
