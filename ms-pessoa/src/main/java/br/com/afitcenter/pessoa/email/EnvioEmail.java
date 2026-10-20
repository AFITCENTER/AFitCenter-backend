package br.com.afitcenter.pessoa.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega os e-mails depois do commit, numa thread à parte ({@code @Async}):
 *
 * <ul>
 *     <li>o servidor de e-mail fica fora da transação: se ela for desfeita, nada é enviado, e um SMTP lento não
 *     segura conexão do banco;</li>
 *     <li>o tempo de resposta não depende do envio, então o "esqueci a senha" responde igual exista ou não a
 *     conta.</li>
 * </ul>
 *
 * <p>Sem fila nem nova tentativa: uma falha vai para o log (sem o texto, que tem o link), e a pessoa pode pedir o
 * e-mail de novo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvioEmail {

    private final ApplicationEventPublisher eventos;
    private final ProvedorEmail provedor;

    /** Agenda o envio para depois do commit da transação atual (ou para já, se não houver transação). */
    public void enviarAposCommit(MensagemEmail email) {
        eventos.publishEvent(new EmailPendente(email));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void entregar(EmailPendente pendente) {
        try {
            provedor.enviar(pendente.email());
        } catch (RuntimeException e) {
            log.error("Não foi possível enviar o e-mail '{}' pelo provedor {}.", pendente.email().assunto(),
                    provedor.nome(), e);
        }
    }

    public record EmailPendente(MensagemEmail email) {
    }
}
