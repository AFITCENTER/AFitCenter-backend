package br.com.afitcenter.planos.config;

import br.com.afitcenter.core.mensageria.Eventos;
import br.com.afitcenter.core.mensageria.fila.Filas;
import br.com.afitcenter.planos.service.PagamentoService;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MensageriaConfig {

    /** Situação das cobranças (paga, vencida, estornada...) publicada pelo ms-pagamentos. */
    @Bean
    Declarables filaCobranca() {
        return Filas.declarar(PagamentoService.FILA, Eventos.PADRAO_COBRANCA);
    }
}
