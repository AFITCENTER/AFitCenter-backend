package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.Auditoria;
import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import br.com.afitcenter.planos.repository.AssinaturaRepository;
import br.com.afitcenter.planos.repository.FaturaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ciclo de vida das assinaturas pagas, executado pelo {@link AgendadorAssinaturas}. Os métodos recebem o
 * instante atual para os testes poderem avançar o relógio.
 *
 * <ol>
 *     <li><b>Renovação:</b> {@code dias-antecedencia-renovacao} antes do fim do período, emite a fatura do
 *     próximo período (preço atual do plano) e a cobrança, com vencimento no último dia pago.</li>
 *     <li><b>Fim do período sem pagamento:</b> a assinatura fica INADIMPLENTE (limites mantidos) e, depois de
 *     {@code dias-carencia}, SUSPENSA (limites do plano padrão até o pagamento).</li>
 *     <li><b>Cancelamento agendado:</b> no fim do período, passa ao plano agendado (gratuito).</li>
 * </ol>
 *
 * <p>Com várias réplicas, cada assinatura é bloqueada e revalidada antes da mudança, e o índice único de
 * renovação por período impede faturas duplicadas.</p>
 */
@Slf4j
@Service
public class CicloAssinaturaService {

    private static final int LOTE = 100;

    private final AssinaturaRepository assinaturaRepository;
    private final FaturaRepository faturaRepository;
    private final AssinaturaService assinaturaService;
    private final FaturaService faturaService;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Duration antecedencia;
    private final Duration carencia;

    public CicloAssinaturaService(AssinaturaRepository assinaturaRepository, FaturaRepository faturaRepository,
                                  AssinaturaService assinaturaService, FaturaService faturaService, Auditoria auditoria,
                                  PlatformTransactionManager transactionManager,
                                  @Value("${afitcenter.planos.dias-antecedencia-renovacao:7}") int diasAntecedencia,
                                  @Value("${afitcenter.planos.dias-carencia:7}") int diasCarencia) {
        this.assinaturaRepository = assinaturaRepository;
        this.faturaRepository = faturaRepository;
        this.assinaturaService = assinaturaService;
        this.faturaService = faturaService;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transactionManager);
        this.antecedencia = Duration.ofDays(diasAntecedencia);
        this.carencia = Duration.ofDays(diasCarencia);
    }

    /** @return quantas faturas de renovação foram emitidas */
    public int emitirRenovacoes(Instant agora) {
        int emitidas = 0;
        for (UUID id : assinaturaRepository.paraRenovar(agora.plus(antecedencia), LOTE)) {
            Fatura fatura;
            try {
                fatura = transacao.execute(status -> criarRenovacao(id, agora));
            } catch (DataIntegrityViolationException e) {
                continue; // outra instância emitiu a mesma renovação
            }
            if (fatura == null) continue;
            emitidas++;
            emitirSemFalhar(fatura);
        }
        return emitidas;
    }

    /** @return quantas assinaturas mudaram de situação */
    public int processarVencimentos(Instant agora) {
        int alteradas = 0;
        List<Fatura> aCancelar = new ArrayList<>();
        for (UUID id : assinaturaRepository.vencidas(agora, agora.minus(carencia), LOTE)) {
            Boolean alterou = transacao.execute(status -> vencer(id, agora, aCancelar));
            if (Boolean.TRUE.equals(alterou)) alteradas++;
        }
        aCancelar.forEach(faturaService::cancelarSemFalhar);
        return alteradas;
    }

    /** Renovações que ficaram sem cobrança porque o ms-pagamentos não respondeu na emissão. */
    public int reemitirCobrancas(Instant agora) {
        int reemitidas = 0;
        for (Fatura fatura : faturaRepository.renovacoesSemCobranca(agora.minus(Duration.ofMinutes(1)), LOTE)) {
            if (emitirSemFalhar(fatura)) reemitidas++;
        }
        return reemitidas;
    }

    private Fatura criarRenovacao(UUID assinaturaId, Instant agora) {
        Assinatura a = assinaturaRepository.bloquear(assinaturaId).orElseThrow();
        boolean renovavel = a.getStatus() == StatusAssinatura.ATIVA && a.pagaNoPeriodo() && !a.cancelamentoAgendado()
                && !a.getPeriodoFim().isAfter(agora.plus(antecedencia));
        if (!renovavel) return null;

        // Plano que deixou de ser vendido no ciclo anual renova no mensal.
        Ciclo ciclo = a.getCiclo() != null && a.getPlano().preco(a.getCiclo()).isPresent() ? a.getCiclo() : Ciclo.MENSAL;
        BigDecimal valor = a.getPlano().preco(ciclo).orElseThrow();
        LocalDate vencimento = LocalDate.ofInstant(a.getPeriodoFim(), FaturaService.FUSO);
        Fatura fatura = faturaRepository.saveAndFlush(Fatura.renovacao(a, ciclo, valor, vencimento, a.getPeriodoFim(),
                ciclo.somar(a.getPeriodoFim())));
        auditoria.registrarSistema(Acoes.CRIACAO, "faturas", fatura.getId(), a.getNutricionistaId(),
                Map.of("tipo", TipoFatura.RENOVACAO.name(), "plano", a.getPlano().getCodigo()));
        return fatura;
    }

    private boolean vencer(UUID assinaturaId, Instant agora, List<Fatura> aCancelar) {
        Assinatura a = assinaturaRepository.bloquear(assinaturaId).orElseThrow();
        if (a.getPeriodoFim() == null || a.getPeriodoFim().isAfter(agora)) return false;

        String motivo;
        if (a.cancelamentoAgendado()) {
            a.mudarParaGratuito(a.getPlanoAgendado());
            aCancelar.addAll(faturaRepository.findAllByAssinaturaIdAndTipoAndStatus(a.getId(), TipoFatura.RENOVACAO,
                    StatusFatura.ABERTA));
            motivo = "cancelamento";
        } else if (a.getStatus() == StatusAssinatura.ATIVA) {
            a.marcarInadimplente();
            motivo = "inadimplencia";
        } else if (a.getStatus() == StatusAssinatura.INADIMPLENTE && !a.getPeriodoFim().plus(carencia).isAfter(agora)) {
            a.suspender();
            motivo = "suspensao";
        } else {
            return false;
        }

        assinaturaService.salvarEPublicar(a);
        auditoria.registrarSistema(Acoes.ALTERACAO, "assinatura", a.getId(), a.getNutricionistaId(),
                Map.of("motivo", motivo, "status", a.getStatus().name(), "plano", a.getPlano().getCodigo()));
        log.info("Assinatura {} do nutricionista {}: {}.", a.getId(), a.getNutricionistaId(), motivo);
        return true;
    }

    private boolean emitirSemFalhar(Fatura fatura) {
        try {
            faturaService.emitirCobranca(fatura);
            return true;
        } catch (RuntimeException e) {
            log.warn("Não foi possível criar a cobrança da fatura {}; nova tentativa na próxima execução.",
                    fatura.getId(), e);
            return false;
        }
    }
}
