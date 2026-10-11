package br.com.afitcenter.core.mensageria.fila;

import br.com.afitcenter.core.mensageria.Eventos;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Declara a fila de um consumidor, a DLQ correspondente e as ligações com o exchange de eventos.
 *
 * <pre>
 * &#64;Bean
 * Declarables filaConsultorio() {
 *     return Filas.declarar("ms-consulta.consultorio", Eventos.PADRAO_CONSULTORIO);
 * }
 * </pre>
 */
public final class Filas {

    private Filas() {
    }

    public static String dlq(String fila) {
        return fila + ".dlq";
    }

    public static Declarables declarar(String fila, String... padroes) {
        TopicExchange eventos = new TopicExchange(Eventos.EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(Eventos.DLX, true, false);

        Queue principal = QueueBuilder.durable(fila)
                .deadLetterExchange(Eventos.DLX)
                .deadLetterRoutingKey(dlq(fila))
                .build();
        Queue mortas = QueueBuilder.durable(dlq(fila)).build();

        List<Declarable> declaraveis = new ArrayList<>();
        declaraveis.add(principal);
        declaraveis.add(mortas);
        declaraveis.add(BindingBuilder.bind(mortas).to(dlx).with(dlq(fila)));
        for (String padrao : padroes)
            declaraveis.add(BindingBuilder.bind(principal).to(eventos).with(padrao));
        return new Declarables(declaraveis);
    }
}
