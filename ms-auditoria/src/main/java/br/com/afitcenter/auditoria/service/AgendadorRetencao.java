package br.com.afitcenter.auditoria.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

/** Retenção diária da trilha. Desligado nos testes, que chamam o {@link RetencaoAuditoria} direto. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "afitcenter.auditoria.agendador.habilitado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AgendadorRetencao {

    private final RetencaoAuditoria retencao;
    private final Clock clock;

    @Scheduled(cron = "${afitcenter.auditoria.retencao-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void executar() {
        int apagados = retencao.apagarAntigos(clock.instant());
        if (apagados > 0)
            log.info("Retenção da auditoria: {} registros com mais de {} dias apagados.", apagados, retencao.retencaoDias());
    }
}
