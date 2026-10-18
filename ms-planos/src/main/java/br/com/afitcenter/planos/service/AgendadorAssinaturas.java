package br.com.afitcenter.planos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;

/**
 * Executa periodicamente o {@link CicloAssinaturaService}. Desligado nos testes
 * ({@code afitcenter.planos.agendador.habilitado=false}), que chamam o serviço com o relógio que quiserem.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "afitcenter.planos.agendador.habilitado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AgendadorAssinaturas {

    private final CicloAssinaturaService ciclo;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${afitcenter.planos.agendador.intervalo-ms:600000}",
            initialDelayString = "${afitcenter.planos.agendador.atraso-inicial-ms:60000}")
    public void executar() {
        Instant agora = clock.instant();
        int renovacoes = ciclo.emitirRenovacoes(agora);
        int vencimentos = ciclo.processarVencimentos(agora);
        int reemitidas = ciclo.reemitirCobrancas(agora);
        if (renovacoes + vencimentos + reemitidas > 0)
            log.info("Agendador de assinaturas: {} renovações, {} mudanças de situação, {} cobranças reemitidas.",
                    renovacoes, vencimentos, reemitidas);
    }
}
