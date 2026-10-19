package br.com.afitcenter.core.assinatura;

import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Consome {@code assinatura.*} e atualiza a réplica. Idempotente: reentregas não mudam nada. */
public class AssinaturaEventosListener {

    private final AssinaturaRefService service;

    public AssinaturaEventosListener(AssinaturaRefService service) {
        this.service = service;
    }

    @RabbitListener(queues = "${afitcenter.replica-assinatura.fila}")
    public void receber(AssinaturaEvento evento) {
        if (evento == null || evento.nutricionistaId() == null || evento.assinaturaId() == null)
            throw new IllegalArgumentException("Evento de assinatura incompleto.");
        service.aplicar(evento);
    }
}
