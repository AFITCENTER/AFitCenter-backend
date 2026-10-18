package br.com.afitcenter.pagamentos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Marca como vencidas as cobranças pendentes. Desligado nos testes, que chamam o serviço direto. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "afitcenter.pagamentos.agendador.habilitado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AgendadorCobrancas {

    private final CobrancaService service;

    @Scheduled(fixedDelayString = "${afitcenter.pagamentos.agendador.intervalo-ms:900000}",
            initialDelayString = "${afitcenter.pagamentos.agendador.atraso-inicial-ms:60000}")
    public void expirarVencidas() {
        int vencidas = service.expirar();
        if (vencidas > 0) log.info("{} cobranças pendentes venceram.", vencidas);
    }
}
