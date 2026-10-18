package br.com.afitcenter.pagamentos.service;

import br.com.afitcenter.core.web.erro.RecursoNaoEncontradoException;
import br.com.afitcenter.pagamentos.model.entity.Cobranca;
import br.com.afitcenter.pagamentos.provedor.NotificacaoPagamento;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento;
import br.com.afitcenter.pagamentos.provedor.Provedores;
import br.com.afitcenter.pagamentos.repository.CobrancaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Webhooks dos provedores. A notificação é autenticada pelo próprio provedor ({@link ProvedorPagamento#interpretar})
 * e registrada pelo id do evento: reentregas (o provedor repete até receber 2xx) são ignoradas.
 *
 * <p>Notificação de cobrança desconhecida responde 2xx mesmo assim (fica registrada): devolver erro faria o
 * provedor repetir para sempre.</p>
 */
@Slf4j
@Service
public class NotificacaoService {

    private final Provedores provedores;
    private final CobrancaRepository repository;
    private final CobrancaService cobrancaService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;

    public NotificacaoService(Provedores provedores, CobrancaRepository repository, CobrancaService cobrancaService,
                              JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.provedores = provedores;
        this.repository = repository;
        this.cobrancaService = cobrancaService;
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    /** @return o que aconteceu com a notificação (para o log e os testes) */
    public String processar(String nomeProvedor, Map<String, String> headers, String corpo) {
        ProvedorPagamento provedor = provedores.porNome(nomeProvedor)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Provedor desconhecido."));
        Map<String, String> semCaixa = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        semCaixa.putAll(headers);
        NotificacaoPagamento notificacao = provedor.interpretar(semCaixa, corpo);

        return transacao.execute(status -> {
            int nova = jdbc.update("""
                    INSERT INTO notificacao_provedor (provedor, id_evento, tipo, id_externo)
                    VALUES (?, ?, ?, ?) ON CONFLICT (provedor, id_evento) DO NOTHING
                    """, provedor.nome(), notificacao.idEvento(), notificacao.tipo().name(), notificacao.idExterno());
            if (nova == 0) return "repetida";

            Optional<Cobranca> cobranca = repository.findByProvedorAndIdExterno(provedor.nome(), notificacao.idExterno())
                    .map(c -> cobrancaService.bloquear(c.getId()));
            String resultado = cobranca.map(c -> cobrancaService.aplicar(c, notificacao)).orElse("cobrança desconhecida");
            if (cobranca.isEmpty())
                log.warn("Notificação {} do provedor {} para a cobrança desconhecida {}.", notificacao.idEvento(),
                        provedor.nome(), notificacao.idExterno());

            jdbc.update("UPDATE notificacao_provedor SET resultado = ? WHERE provedor = ? AND id_evento = ?",
                    resultado, provedor.nome(), notificacao.idEvento());
            return resultado;
        });
    }
}
