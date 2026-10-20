package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.FaturaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.MudancaPlanoRequest;
import br.com.afitcenter.planos.model.dto.Dtos.MudancaPlanoResponse;
import br.com.afitcenter.planos.model.entity.Assinatura;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.entity.Plano;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.model.enums.Enums.StatusFatura;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import br.com.afitcenter.planos.repository.AssinaturaRepository;
import br.com.afitcenter.planos.repository.FaturaRepository;
import br.com.afitcenter.planos.repository.PlanoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mudança de plano pedida pelo nutricionista.
 *
 * <ul>
 *     <li><b>Para um plano pago:</b> emite uma fatura e a cobrança; o plano só muda quando o ms-pagamentos
 *     confirmar o pagamento, e o novo período começa nesse momento (sem pró-rata do período anterior).
 *     Uma escolha nova substitui a mudança anterior ainda não paga.</li>
 *     <li><b>Para um plano gratuito, saindo de um pago:</b> é o cancelamento. Em dia, vale no fim do período
 *     já pago; inadimplente ou suspensa, vale na hora. A renovação em aberto é cancelada nos dois casos.</li>
 *     <li><b>Entre planos gratuitos:</b> imediata.</li>
 * </ul>
 *
 * <p>Só planos ativos do catálogo público (ou o padrão) podem ser escolhidos, como em {@code GET /v1/planos}.</p>
 */
@Slf4j
@Service
public class MudancaPlanoService {

    private final AssinaturaService assinaturaService;
    private final AssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;
    private final FaturaRepository faturaRepository;
    private final FaturaService faturaService;
    private final TransactionTemplate transacao;

    public MudancaPlanoService(AssinaturaService assinaturaService, AssinaturaRepository assinaturaRepository,
                               PlanoRepository planoRepository, FaturaRepository faturaRepository,
                               FaturaService faturaService, PlatformTransactionManager transactionManager) {
        this.assinaturaService = assinaturaService;
        this.assinaturaRepository = assinaturaRepository;
        this.planoRepository = planoRepository;
        this.faturaRepository = faturaRepository;
        this.faturaService = faturaService;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public MudancaPlanoResponse mudar(UUID nutricionistaId, MudancaPlanoRequest dados) {
        Assinatura assinatura = assinaturaService.obterOuCriar(nutricionistaId);
        // Plano fora do catálogo (cortesia, parceiro) não é contratado por aqui; quem já está nele pode mudar o ciclo.
        Plano destino = planoRepository.findByIdAndAtivoTrue(dados.planoId())
                .filter(plano -> plano.isPublico() || plano.isPadrao()
                        || plano.getId().equals(assinatura.getPlano().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano não encontrado."));

        if (destino.gratuito())
            return new MudancaPlanoResponse(paraGratuito(assinatura, destino), null, null);

        Ciclo ciclo = validarPago(assinatura, destino, dados);
        BigDecimal valor = destino.preco(ciclo).orElseThrow();

        // A escolha nova substitui a mudança anterior que ainda não foi paga.
        abertas(assinatura, TipoFatura.MUDANCA_PLANO).forEach(faturaService::cancelarComCobranca);

        Fatura fatura = transacao.execute(status -> faturaRepository.saveAndFlush(Fatura.mudancaDePlano(
                assinatura, destino, ciclo, valor, dados.formaPagamento(), faturaService.vencimentoPadrao())));
        CobrancaResponse cobranca;
        try {
            cobranca = faturaService.emitirCobranca(fatura);
        } catch (RuntimeException e) {
            // Sem cobrança a fatura não serve para nada: o nutricionista tenta de novo.
            transacao.executeWithoutResult(status -> faturaRepository.bloquear(fatura.getId()).ifPresent(f -> {
                f.cancelar();
                faturaRepository.saveAndFlush(f);
            }));
            throw e;
        }

        Fatura comCobranca = faturaRepository.findById(fatura.getId()).orElseThrow();
        return new MudancaPlanoResponse(assinaturaService.resposta(assinatura), FaturaResponse.de(comCobranca), cobranca);
    }

    /** Volta ao plano padrão (no fim do período pago, se estiver em dia). */
    public AssinaturaResponse cancelar(UUID nutricionistaId) {
        Assinatura assinatura = assinaturaService.obterOuCriar(nutricionistaId);
        if (assinatura.getPlano().gratuito())
            throw new RegraNegocioException("Sua assinatura já está no plano gratuito.");
        if (assinatura.cancelamentoAgendado())
            throw new RegraNegocioException("O cancelamento já está agendado para o fim do período.");
        return paraGratuito(assinatura, assinaturaService.padrao());
    }

    public AssinaturaResponse desfazerCancelamento(UUID nutricionistaId) {
        Assinatura assinatura = assinaturaService.obterOuCriar(nutricionistaId);
        return transacao.execute(status -> {
            Assinatura atual = bloquear(assinatura.getId());
            if (!atual.cancelamentoAgendado())
                throw new RegraNegocioException("Não há cancelamento agendado.");
            atual.desfazerAgendamento();
            return assinaturaService.resposta(assinaturaService.salvarEPublicar(atual));
        });
    }

    private AssinaturaResponse paraGratuito(Assinatura assinatura, Plano destino) {
        if (assinatura.getPlano().getId().equals(destino.getId()))
            throw new RegraNegocioException("Você já está neste plano.");

        // Quem cancela não renova: a renovação já emitida (e a mudança não paga) são canceladas.
        abertas(assinatura, TipoFatura.MUDANCA_PLANO).forEach(faturaService::cancelarComCobranca);
        abertas(assinatura, TipoFatura.RENOVACAO).forEach(faturaService::cancelarComCobranca);
        boolean imediata = assinatura.getPlano().gratuito() || assinatura.getStatus() != StatusAssinatura.ATIVA;

        return transacao.execute(status -> {
            Assinatura atual = bloquear(assinatura.getId());
            if (imediata) atual.mudarParaGratuito(destino);
            else atual.agendarMudanca(destino);
            return assinaturaService.resposta(assinaturaService.salvarEPublicar(atual));
        });
    }

    private Ciclo validarPago(Assinatura assinatura, Plano destino, MudancaPlanoRequest dados) {
        if (dados.ciclo() == null)
            throw new RegraNegocioException("Informe o ciclo de cobrança (MENSAL ou ANUAL).");
        if (dados.formaPagamento() == null)
            throw new RegraNegocioException("Informe a forma de pagamento.");
        if (destino.preco(dados.ciclo()).isEmpty())
            throw new RegraNegocioException("Este plano não é vendido no ciclo " + dados.ciclo().name().toLowerCase() + ".");
        if (assinatura.getStatus() != StatusAssinatura.ATIVA)
            throw new RegraNegocioException("Há uma renovação sem pagamento. Pague a fatura em aberto ou cancele a "
                    + "assinatura antes de mudar de plano.");
        boolean mesmoPlano = assinatura.getPlano().getId().equals(destino.getId())
                && Objects.equals(assinatura.getCiclo(), dados.ciclo());
        if (mesmoPlano)
            throw new RegraNegocioException(assinatura.cancelamentoAgendado()
                    ? "Você já está neste plano. Para continuar nele, desfaça o cancelamento."
                    : "Você já está neste plano.");
        return dados.ciclo();
    }

    private List<Fatura> abertas(Assinatura assinatura, TipoFatura tipo) {
        return faturaRepository.findAllByAssinaturaIdAndTipoAndStatus(assinatura.getId(), tipo, StatusFatura.ABERTA);
    }

    private Assinatura bloquear(UUID id) {
        return assinaturaRepository.bloquear(id).orElseThrow();
    }
}
