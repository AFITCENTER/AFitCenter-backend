package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.auditoria.Acoes;
import br.com.afitcenter.core.auditoria.Auditoria;
import br.com.afitcenter.core.mensageria.evento.CobrancaEvento;
import br.com.afitcenter.planos.integracao.Cobrancas;
import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import br.com.afitcenter.planos.repository.AssinaturaRepository;
import br.com.afitcenter.planos.repository.FaturaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aplica à assinatura as mudanças de situação das cobranças ({@code cobranca.*} do ms-pagamentos).
 *
 * <ul>
 *     <li><b>Paga:</b> mudança de plano começa um período novo a partir do pagamento; renovação estende o
 *     período (ou recomeça a partir do pagamento, se a assinatura estava suspensa). Se a cobrança paga não era a
 *     vigente da fatura (Pix antigo pago depois de gerar outro), a vigente é cancelada. Pagamento que não se
 *     aplica (fatura já paga por outra cobrança ou cancelada, renovação de um plano ou período que a assinatura já
 *     deixou) não muda a assinatura e vai para o log e a trilha de auditoria, para o estorno manual.</li>
 *     <li><b>Vencida, recusada ou cancelada:</b> a mudança de plano é desfeita; a renovação continua em aberto
 *     para uma nova cobrança.</li>
 *     <li><b>Estornada:</b> o período pago por ela deixa de valer.</li>
 * </ul>
 *
 * <p>Idempotente: reentregas e eventos de cobranças substituídas não mudam nada.</p>
 */
@Slf4j
@Service
public class PagamentoService {

    public static final String FILA = "ms-planos.cobranca";

    private final FaturaRepository faturaRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final AssinaturaService assinaturaService;
    private final FaturaService faturaService;
    private final Cobrancas cobrancas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public PagamentoService(FaturaRepository faturaRepository, AssinaturaRepository assinaturaRepository,
                            AssinaturaService assinaturaService, FaturaService faturaService, Cobrancas cobrancas,
                            Auditoria auditoria, PlatformTransactionManager transactionManager, Clock clock) {
        this.faturaRepository = faturaRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.assinaturaService = assinaturaService;
        this.faturaService = faturaService;
        this.cobrancas = cobrancas;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @RabbitListener(queues = FILA)
    public void receber(CobrancaEvento evento) {
        if (evento == null || evento.cobrancaId() == null || evento.referenciaId() == null || evento.status() == null)
            throw new IllegalArgumentException("Evento de cobrança incompleto.");
        if (!Cobrancas.ORIGEM.equals(evento.origem())) return;

        List<Runnable> depois = transacao.execute(status -> aplicar(evento));
        // Fora da transação: cancelar no ms-pagamentos é uma chamada de rede.
        depois.forEach(Runnable::run);
    }

    /** @return o que fazer depois da confirmação: cancelar cobranças e faturas que o pagamento tornou inúteis */
    private List<Runnable> aplicar(CobrancaEvento evento) {
        Fatura fatura = faturaRepository.bloquear(evento.referenciaId()).orElse(null);
        if (fatura == null) {
            log.warn("A cobrança {} aponta para a fatura {}, que não existe.", evento.cobrancaId(), evento.referenciaId());
            return List.of();
        }
        return switch (evento.status()) {
            case "PAGA" -> paga(fatura, evento);
            case "ESTORNADA" -> estornada(fatura, evento);
            case "VENCIDA", "RECUSADA", "CANCELADA" -> naoPaga(fatura, evento);
            default -> List.of();
        };
    }

    private List<Runnable> paga(Fatura fatura, CobrancaEvento evento) {
        if (fatura.getStatus() == StatusFatura.PAGA) {
            // Reentrega do mesmo pagamento: nada a fazer. Outra cobrança da mesma fatura: o dinheiro entrou duas vezes.
            if (!evento.cobrancaId().equals(fatura.getCobrancaId()))
                pagamentoSemEfeito("PAGAMENTO_DUPLICADO", fatura, evento,
                        "a fatura já foi paga pela cobrança " + fatura.getCobrancaId());
            return List.of();
        }
        if (!fatura.aberta()) {
            // Pago depois de cancelada (ex.: Pix pago no último instante): precisa de estorno manual.
            pagamentoSemEfeito("PAGAMENTO_FATURA_" + fatura.getStatus(), fatura, evento,
                    "a fatura está " + fatura.getStatus());
            return List.of();
        }

        Assinatura assinatura = assinaturaRepository.bloquear(fatura.getAssinaturaId()).orElseThrow();
        Instant pagoEm = evento.pagoEm() != null ? evento.pagoEm() : clock.instant();
        List<Runnable> depois = new ArrayList<>();
        UUID vigente = fatura.getCobrancaId();
        if (vigente != null && !vigente.equals(evento.cobrancaId())) {
            // Pagou uma cobrança anterior da mesma fatura: a vigente não pode ser paga de novo.
            depois.add(() -> cancelarCobrancaSemFalhar(vigente));
        }

        if (fatura.getTipo() == TipoFatura.RENOVACAO && !renovaOPeriodoAtual(fatura, assinatura)) {
            // A assinatura mudou depois da emissão (mudança de plano paga, volta ao gratuito): aplicar a renovação
            // trocaria o plano atual pelo antigo. O pagamento fica registrado na fatura para o estorno.
            fatura.pagar(pagoEm, fatura.getPeriodoInicio(), fatura.getPeriodoFim());
            fatura.vincularCobranca(evento.cobrancaId());
            faturaRepository.saveAndFlush(fatura);
            pagamentoSemEfeito("PAGAMENTO_RENOVACAO_SUPERADA", fatura, evento,
                    "a assinatura já não está no plano e no período que a renovação estendia");
            return depois;
        }

        boolean recomeca = fatura.getTipo() == TipoFatura.MUDANCA_PLANO
                || assinatura.getStatus() == StatusAssinatura.SUSPENSA;
        Instant inicio = recomeca ? pagoEm : fatura.getPeriodoInicio();
        Instant fim = fatura.getCiclo().somar(inicio);
        if (fatura.getTipo() == TipoFatura.MUDANCA_PLANO)
            renovacoesNaoUtilizadas(assinatura, pagoEm);
        fatura.pagar(pagoEm, inicio, fim);
        fatura.vincularCobranca(evento.cobrancaId());
        faturaRepository.saveAndFlush(fatura);

        Instant inicioVigencia = recomeca || assinatura.getPeriodoInicio() == null ? inicio : assinatura.getPeriodoInicio();
        assinatura.iniciarPeriodo(fatura.getPlano(), fatura.getCiclo(), fatura.getFormaPagamento(), inicioVigencia, fim);
        assinaturaService.salvarEPublicar(assinatura);
        auditoria.registrarSistema(Acoes.ALTERACAO, "assinatura", assinatura.getId(), assinatura.getNutricionistaId(),
                Map.of("motivo", "pagamento", "faturaId", fatura.getId().toString(),
                        "cobrancaId", evento.cobrancaId().toString(), "plano", fatura.getPlano().getCodigo()));

        if (fatura.getTipo() == TipoFatura.MUDANCA_PLANO)
            // O plano novo começa agora: a renovação do plano anterior não vale mais.
            faturaRepository.findAllByAssinaturaIdAndTipoAndStatus(assinatura.getId(), TipoFatura.RENOVACAO,
                    StatusFatura.ABERTA).forEach(r -> depois.add(() -> faturaService.cancelarSemFalhar(r)));
        return depois;
    }

    /** A renovação estende o período atual do mesmo plano, e não um que a assinatura já deixou para trás. */
    private static boolean renovaOPeriodoAtual(Fatura renovacao, Assinatura assinatura) {
        return renovacao.getPlano().getId().equals(assinatura.getPlano().getId())
                && renovacao.getPeriodoInicio().equals(assinatura.getPeriodoFim());
    }

    /**
     * Renovações já pagas cujo período ainda não começou: a mudança de plano começa agora (sem pró-rata) e elas não
     * serão usadas. Vão para a trilha para o estorno.
     */
    private void renovacoesNaoUtilizadas(Assinatura assinatura, Instant pagoEm) {
        faturaRepository.findAllByAssinaturaIdAndTipoAndStatus(assinatura.getId(), TipoFatura.RENOVACAO,
                        StatusFatura.PAGA).stream()
                .filter(renovacao -> renovacao.getPeriodoInicio().isAfter(pagoEm))
                .forEach(renovacao -> {
                    log.error("A renovação {} já paga não será usada: a mudança de plano começou antes do período dela. "
                            + "Verifique e estorne manualmente.", renovacao.getId());
                    auditoria.registrarSistema("RENOVACAO_PAGA_NAO_UTILIZADA", "faturas", renovacao.getId(),
                            renovacao.getNutricionistaId(), Map.of("cobrancaId", String.valueOf(renovacao.getCobrancaId())));
                });
    }

    /** Pagamento confirmado que não muda a assinatura: o valor precisa ser estornado manualmente. */
    private void pagamentoSemEfeito(String acao, Fatura fatura, CobrancaEvento evento, String motivo) {
        log.error("Pagamento {} confirmado para a fatura {}, mas {}. Verifique e estorne manualmente.",
                evento.cobrancaId(), fatura.getId(), motivo);
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("cobrancaId", evento.cobrancaId().toString());
        if (fatura.getCobrancaId() != null && !fatura.getCobrancaId().equals(evento.cobrancaId()))
            detalhes.put("cobrancaDaFatura", fatura.getCobrancaId().toString());
        auditoria.registrarSistema(acao, "faturas", fatura.getId(), fatura.getNutricionistaId(), detalhes);
    }

    private void cancelarCobrancaSemFalhar(UUID cobrancaId) {
        try {
            cobrancas.cancelar(cobrancaId);
        } catch (RuntimeException e) {
            log.error("A cobrança {} continua pendente embora a fatura já tenha sido paga por outra cobrança. "
                    + "Cancele-a no ms-pagamentos.", cobrancaId, e);
        }
    }

    private List<Runnable> naoPaga(Fatura fatura, CobrancaEvento evento) {
        if (!fatura.aberta() || !evento.cobrancaId().equals(fatura.getCobrancaId())) return List.of();
        if (fatura.getTipo() == TipoFatura.MUDANCA_PLANO) {
            fatura.cancelar();
            faturaRepository.saveAndFlush(fatura);
        }
        auditoria.registrarSistema(Acoes.ALTERACAO, "faturas", fatura.getId(), fatura.getNutricionistaId(),
                Map.of("motivo", "cobranca " + evento.status().toLowerCase(), "cobrancaId", evento.cobrancaId().toString()));
        return List.of();
    }

    private List<Runnable> estornada(Fatura fatura, CobrancaEvento evento) {
        if (fatura.getStatus() != StatusFatura.PAGA) return List.of();
        fatura.estornar();
        faturaRepository.saveAndFlush(fatura);

        Assinatura assinatura = assinaturaRepository.bloquear(fatura.getAssinaturaId()).orElseThrow();
        if (assinatura.getPeriodoFim() != null && assinatura.getPeriodoFim().equals(fatura.getPeriodoFim())) {
            boolean renovacaoAindaNaoComecou = fatura.getTipo() == TipoFatura.RENOVACAO
                    && clock.instant().isBefore(fatura.getPeriodoInicio());
            if (renovacaoAindaNaoComecou)
                assinatura.iniciarPeriodo(assinatura.getPlano(), assinatura.getCiclo(), assinatura.getFormaPagamento(),
                        assinatura.getPeriodoInicio(), fatura.getPeriodoInicio());
            else
                assinatura.mudarParaGratuito(assinaturaService.padrao());
            assinaturaService.salvarEPublicar(assinatura);
        }
        auditoria.registrarSistema(Acoes.ALTERACAO, "assinatura", assinatura.getId(), assinatura.getNutricionistaId(),
                Map.of("motivo", "estorno", "faturaId", fatura.getId().toString(),
                        "cobrancaId", evento.cobrancaId().toString()));
        return List.of();
    }
}
