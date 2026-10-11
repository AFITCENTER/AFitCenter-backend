package br.com.afitcenter.consulta.config;

import br.com.afitcenter.consulta.replica.ConsultorioRefService;
import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.fila.Filas;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas do ms-consulta. A de pacientes ({@code ms-consulta.paciente}) é declarada pelo core-paciente
 * a partir de {@code afitcenter.replica-paciente.fila}.
 */
@Configuration
public class MensageriaConfig {

    @Bean
    Declarables filaConsultorio() {
        return Filas.declarar(ConsultorioRefService.FILA, Eventos.PADRAO_CONSULTORIO);
    }
}
