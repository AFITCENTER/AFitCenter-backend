package br.com.afitcenter.auditoria.service;

import br.com.afitcenter.auditoria.repository.GravacaoRegistros;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Apaga os registros mais antigos que {@code afitcenter.auditoria.retencao-dias} ({@code 0} = guardar para sempre).
 * Executado uma vez por dia pelo {@link AgendadorRetencao}.
 */
@Service
public class RetencaoAuditoria {

    private static final int LOTE = 5_000;

    private final GravacaoRegistros gravacao;
    private final int retencaoDias;

    public RetencaoAuditoria(GravacaoRegistros gravacao,
                             @Value("${afitcenter.auditoria.retencao-dias:1825}") int retencaoDias) {
        this.gravacao = gravacao;
        this.retencaoDias = retencaoDias;
    }

    /** @return quantos registros foram apagados */
    public int apagarAntigos(Instant agora) {
        if (retencaoDias <= 0) return 0;
        Instant limite = agora.minus(Duration.ofDays(retencaoDias));
        int total = 0;
        int apagados;
        do {
            apagados = gravacao.apagarAnterioresA(limite, LOTE);
            total += apagados;
        } while (apagados == LOTE);
        return total;
    }

    public int retencaoDias() {
        return retencaoDias;
    }
}
