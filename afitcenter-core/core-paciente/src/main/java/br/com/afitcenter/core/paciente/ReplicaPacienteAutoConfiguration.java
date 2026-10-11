package br.com.afitcenter.core.paciente;

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

/**
 * Liga a réplica do paciente num serviço consumidor. Basta a dependência {@code core-paciente},
 * a tabela {@code paciente_ref} na migração e a propriedade com o nome da fila:
 *
 * <pre>
 * afitcenter.replica-paciente.fila=ms-consulta.paciente
 * </pre>
 */
@AutoConfiguration(after = {MensageriaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnProperty(name = "afitcenter.replica-paciente.fila")
@EnableFeignClients(clients = PacienteInternoClient.class)
public class ReplicaPacienteAutoConfiguration {

    @Bean
    public PacienteRefRepository pacienteRefRepository(JdbcTemplate jdbcTemplate) {
        return new PacienteRefRepository(jdbcTemplate);
    }

    @Bean
    public PacienteRefService pacienteRefService(PacienteRefRepository repository, PacienteInternoClient client) {
        return new PacienteRefService(repository, client);
    }

    @Bean
    public PacienteEventosListener pacienteEventosListener(PacienteRefService service) {
        return new PacienteEventosListener(service);
    }

    @Bean
    public Declarables filaReplicaPaciente(@Value("${afitcenter.replica-paciente.fila}") String fila) {
        return Filas.declarar(fila, Eventos.PADRAO_PACIENTE);
    }
}
