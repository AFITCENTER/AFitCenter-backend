package br.com.afitcenter.pessoa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Liga o {@code @Async} do envio de e-mail ({@code EnvioEmail}), que roda no executor padrão do Spring Boot
 * ({@code spring.task.execution.*}). No desligamento, o executor espera os envios pendentes.
 */
@Configuration
@EnableAsync
public class AssincronoConfig {
}
