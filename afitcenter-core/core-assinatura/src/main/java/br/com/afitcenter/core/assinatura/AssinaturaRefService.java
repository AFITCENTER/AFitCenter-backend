package br.com.afitcenter.core.assinatura;

import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Aplica os limites do plano usando a réplica local da assinatura.
 *
 * <p>Na primeira vez que o nutricionista cria algo limitado a réplica ainda está vazia: a assinatura é
 * buscada no ms-planos e gravada (read-through). Se o ms-planos estiver fora do ar nesse momento, a
 * criação é <b>permitida</b> e o fato vai para o log: um limite comercial não deve parar o atendimento.</p>
 *
 * <p>A réplica buscada é gravada numa transação própria: se o limite recusar a criação, o rollback da
 * criação não leva a réplica junto (senão cada nova tentativa voltaria a chamar o ms-planos).</p>
 */
@Slf4j
public class AssinaturaRefService {

    private final AssinaturaRefRepository repository;
    private final AssinaturaInternoClient client;
    private final TransactionOperations emTransacaoPropria;

    public AssinaturaRefService(AssinaturaRefRepository repository, AssinaturaInternoClient client,
                                TransactionOperations emTransacaoPropria) {
        this.repository = repository;
        this.client = client;
        this.emTransacaoPropria = emTransacaoPropria;
    }

    /**
     * Recusa (422) a criação quando o uso atual já atingiu o limite do plano. Chame dentro da transação
     * que cria o recurso: a linha da réplica fica bloqueada até o fim dela.
     *
     * @param usoAtual quantidade atual do recurso, só calculada se houver limite
     */
    public void exigirDisponivel(UUID nutricionistaId, Limite limite, LongSupplier usoAtual) {
        Optional<AssinaturaRef> ref = repository.bloquear(nutricionistaId);
        if (ref.isEmpty()) {
            if (!sincronizar(nutricionistaId)) return;
            ref = repository.bloquear(nutricionistaId);
        }

        Integer maximo = ref.map(r -> r.limite(limite)).orElse(null);
        if (maximo == null) return;
        if (usoAtual.getAsLong() >= maximo)
            throw new RegraNegocioException(mensagem(ref.get(), limite, maximo));
    }

    public void aplicar(AssinaturaEvento evento) {
        boolean alterou = repository.aplicar(AssinaturaRef.de(evento));
        log.debug("Evento {} da assinatura do nutricionista {} (versão {}) {}.", evento.tipo(),
                evento.nutricionistaId(), evento.versao(), alterou ? "aplicado" : "ignorado por ser antigo");
    }

    /** @return {@code false} se o ms-planos não respondeu (a criação segue sem limite) */
    private boolean sincronizar(UUID nutricionistaId) {
        try {
            AssinaturaRef remota = client.buscar(nutricionistaId);
            if (remota == null) return false;
            emTransacaoPropria.executeWithoutResult(status -> repository.aplicar(remota));
            return true;
        } catch (RuntimeException e) {
            log.warn("Não foi possível consultar a assinatura do nutricionista {} no ms-planos; o limite do plano "
                    + "não foi aplicado nesta operação.", nutricionistaId, e);
            return false;
        }
    }

    private static String mensagem(AssinaturaRef ref, Limite limite, int maximo) {
        if (ref.suspensa())
            return "Sua assinatura está suspensa por falta de pagamento e, até a regularização, valem os limites do "
                    + "plano gratuito: até " + maximo + " " + limite.descricao() + ".";
        return "Seu plano permite até " + maximo + " " + limite.descricao()
                + ". Mude de plano para cadastrar mais.";
    }
}
