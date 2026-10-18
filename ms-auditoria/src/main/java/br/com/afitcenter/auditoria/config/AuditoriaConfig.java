package br.com.afitcenter.auditoria.config;

import br.com.afitcenter.auditoria.service.RegistroService;
import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.fila.Filas;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuditoriaConfig {

    /** Uma fila para a trilha de todos os serviços ({@code auditoria.*}), com DLQ. */
    @Bean
    Declarables filaRegistros() {
        return Filas.declarar(RegistroService.FILA, Eventos.PADRAO_AUDITORIA);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
