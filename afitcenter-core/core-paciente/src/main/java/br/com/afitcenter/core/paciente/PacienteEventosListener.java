package br.com.afitcenter.core.paciente;

import br.com.afitcenter.core.mensageria.evento.PacienteEvento;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Consome {@code paciente.*} e atualiza a réplica. Idempotente: reentregas não mudam nada. */
public class PacienteEventosListener {

    private final PacienteRefService service;

    public PacienteEventosListener(PacienteRefService service) {
        this.service = service;
    }

    @RabbitListener(queues = "${afitcenter.replica-paciente.fila}")
    public void receber(PacienteEvento evento) {
        if (evento == null || evento.pacienteId() == null || evento.nutricionistaId() == null)
            throw new IllegalArgumentException("Evento de paciente incompleto.");
        service.aplicar(evento);
    }
}
