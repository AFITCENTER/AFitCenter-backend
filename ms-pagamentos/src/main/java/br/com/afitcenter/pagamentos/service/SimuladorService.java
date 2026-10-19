package br.com.afitcenter.pagamentos.service;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.model.entity.Cobranca;
import br.com.afitcenter.pagamentos.model.enums.Enums.FormaPagamento;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento.Tipo;
import br.com.afitcenter.pagamentos.provedor.ProvedorSimulado;
import br.com.afitcenter.pagamentos.repository.CobrancaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * "Paga" ou "recusa" uma cobrança do simulador, montando e assinando a notificação como o provedor faria e
 * processando-a pelo mesmo caminho do webhook. Assim o fluxo inteiro (evento, ms-planos, auditoria) é exercitado.
 */
@Service
@ConditionalOnProperty(name = "afitcenter.pagamentos.simulador.habilitado", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SimuladorService {

    private final ProvedorSimulado simulado;
    private final NotificacaoService notificacoes;
    private final CobrancaRepository repository;
    private final CobrancaService cobrancaService;

    public CobrancaResponse pagar(UUID cobrancaId) {
        Cobranca cobranca = daSimulacao(cobrancaId);
        notificar(cobranca, Tipo.PAGAMENTO_CONFIRMADO);
        return cobrancaService.buscarAdmin(cobrancaId);
    }

    public CobrancaResponse recusar(UUID cobrancaId) {
        Cobranca cobranca = daSimulacao(cobrancaId);
        if (cobranca.getFormaPagamento() != FormaPagamento.CARTAO_CREDITO)
            throw new RegraNegocioException("Só pagamentos com cartão podem ser recusados.");
        notificar(cobranca, Tipo.PAGAMENTO_RECUSADO);
        return cobrancaService.buscarAdmin(cobrancaId);
    }

    private void notificar(Cobranca cobranca, Tipo tipo) {
        String corpo = simulado.notificacao(cobranca.getIdExterno(), tipo,
                tipo == Tipo.PAGAMENTO_CONFIRMADO ? cobranca.getValor() : null);
        notificacoes.processar(ProvedorSimulado.NOME, Map.of(ProvedorSimulado.HEADER_ASSINATURA, simulado.assinar(corpo)),
                corpo);
    }

    /** O nutricionista só simula as próprias cobranças; o administrador, qualquer uma. */
    private Cobranca daSimulacao(UUID cobrancaId) {
        Cobranca cobranca = UsuarioAutenticado.possui(Papel.ADMIN)
                ? repository.findById(cobrancaId).orElseThrow(() -> new RecursoNaoEncontradoException("Cobrança não encontrada."))
                : cobrancaService.doNutricionista(UsuarioAutenticado.nutricionistaId(), cobrancaId);
        if (!ProvedorSimulado.NOME.equals(cobranca.getProvedor()) || !cobranca.criadaNoProvedor())
            throw new RegraNegocioException("Esta cobrança não é do provedor simulado.");
        return cobranca;
    }
}
