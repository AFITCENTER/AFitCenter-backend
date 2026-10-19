package br.com.afitcenter.planos.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.planos.integracao.Cobrancas;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.FaturaResponse;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import br.com.afitcenter.planos.repository.AssinaturaRepository;
import br.com.afitcenter.planos.repository.FaturaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Faturas e as cobranças delas no ms-pagamentos.
 *
 * <p>As chamadas ao ms-pagamentos ficam fora das transações de banco (não seguram conexão durante a rede).
 * A ordem é sempre: cancelar a cobrança anterior lá, gravar aqui, criar a nova lá, vincular aqui. Se a
 * criação falhar no meio, a renovação fica sem cobrança e o agendador tenta de novo.</p>
 */
@Slf4j
@Service
public class FaturaService {

    /** Vencimentos e "hoje" no fuso de quem usa o sistema. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final FaturaRepository repository;
    private final AssinaturaRepository assinaturaRepository;
    private final Cobrancas cobrancas;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private final int diasVencimento;

    public FaturaService(FaturaRepository repository, AssinaturaRepository assinaturaRepository, Cobrancas cobrancas,
                         PlatformTransactionManager transactionManager, Clock clock,
                         @Value("${afitcenter.planos.dias-vencimento:3}") int diasVencimento) {
        this.repository = repository;
        this.assinaturaRepository = assinaturaRepository;
        this.cobrancas = cobrancas;
        this.transacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.diasVencimento = diasVencimento;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FaturaResponse> listar(UUID nutricionistaId, int pagina, int tamanho) {
        var page = repository.findAllByNutricionistaId(nutricionistaId, PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "criadoEm")));
        return PaginaResponse.de(page, FaturaResponse::de);
    }

    @Transactional(readOnly = true)
    public FaturaResponse buscar(UUID nutricionistaId, UUID id) {
        return FaturaResponse.de(entidade(nutricionistaId, id));
    }

    /** Vencimento de uma cobrança emitida hoje. */
    public LocalDate vencimentoPadrao() {
        return LocalDate.now(clock.withZone(FUSO)).plusDays(diasVencimento);
    }

    /**
     * Nova cobrança para uma fatura em aberto: Pix vencido, cartão recusado ou troca da forma de pagamento.
     * A cobrança anterior é cancelada antes. Numa renovação, a forma escolhida passa a valer para as próximas.
     */
    public CobrancaResponse novaCobranca(UUID nutricionistaId, UUID faturaId, FormaPagamento formaPagamento) {
        Fatura atual = transacao.execute(status -> exigirAberta(entidade(nutricionistaId, faturaId)));
        if (atual.getCobrancaId() != null) cobrancas.cancelar(atual.getCobrancaId());

        Fatura preparada = transacao.execute(status -> {
            Fatura fatura = exigirAberta(bloquear(faturaId));
            LocalDate vencimento = fatura.getVencimento().isAfter(vencimentoPadrao())
                    ? fatura.getVencimento() : vencimentoPadrao();
            fatura.novaTentativa(formaPagamento, vencimento);
            if (fatura.getTipo() == TipoFatura.RENOVACAO)
                assinaturaRepository.bloquear(fatura.getAssinaturaId())
                        .ifPresent(a -> a.alterarFormaPagamento(formaPagamento));
            return repository.saveAndFlush(fatura);
        });
        return emitirCobranca(preparada);
    }

    /** Cancela uma mudança de plano não paga. Renovações não: para não renovar, cancela-se a assinatura. */
    public void cancelar(UUID nutricionistaId, UUID faturaId) {
        Fatura fatura = transacao.execute(status -> exigirAberta(entidade(nutricionistaId, faturaId)));
        if (fatura.getTipo() == TipoFatura.RENOVACAO)
            throw new RegraNegocioException("Faturas de renovação não podem ser canceladas. Para não renovar, "
                    + "cancele a assinatura.");
        cancelarComCobranca(fatura);
    }

    /** Cria a cobrança da tentativa atual e a vincula à fatura. */
    public CobrancaResponse emitirCobranca(Fatura fatura) {
        CobrancaResponse cobranca = cobrancas.emitir(fatura);
        transacao.executeWithoutResult(status -> {
            Fatura atual = bloquear(fatura.getId());
            if (atual.aberta() && atual.getTentativasCobranca() == fatura.getTentativasCobranca()
                    && atual.getCobrancaId() == null) {
                atual.vincularCobranca(cobranca.id());
                repository.saveAndFlush(atual);
            }
        });
        return cobranca;
    }

    /** Cancela a cobrança no ms-pagamentos e depois a fatura (409 se a cobrança já foi paga). */
    public void cancelarComCobranca(Fatura fatura) {
        if (fatura.getCobrancaId() != null) cobrancas.cancelar(fatura.getCobrancaId());
        transacao.executeWithoutResult(status -> {
            Fatura atual = bloquear(fatura.getId());
            if (atual.aberta()) {
                atual.cancelar();
                repository.saveAndFlush(atual);
            }
        });
    }

    /** Versão que só registra no log: usada depois que algo maior (um pagamento) já foi confirmado. */
    public void cancelarSemFalhar(Fatura fatura) {
        try {
            cancelarComCobranca(fatura);
        } catch (RuntimeException e) {
            log.warn("Não foi possível cancelar a fatura {} (cobrança {}); ela vencerá sem efeito.",
                    fatura.getId(), fatura.getCobrancaId(), e);
        }
    }

    private Fatura entidade(UUID nutricionistaId, UUID id) {
        return repository.findByIdAndNutricionistaId(id, nutricionistaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada."));
    }

    private Fatura bloquear(UUID id) {
        return repository.bloquear(id).orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada."));
    }

    private static Fatura exigirAberta(Fatura fatura) {
        if (!fatura.aberta()) throw new RegraNegocioException("Esta fatura não está em aberto.");
        return fatura;
    }
}
