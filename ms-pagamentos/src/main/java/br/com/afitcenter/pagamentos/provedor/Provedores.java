package br.com.afitcenter.pagamentos.provedor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provedores disponíveis. Novas cobranças vão para o ativo ({@code afitcenter.pagamentos.provedor}); cobranças
 * antigas e webhooks continuam no provedor em que foram criadas, para a troca de provedor não quebrar nada.
 */
@Slf4j
@Component
public class Provedores {

    private final Map<String, ProvedorPagamento> porNome;
    private final ProvedorPagamento ativo;

    public Provedores(List<ProvedorPagamento> provedores,
                      @Value("${afitcenter.pagamentos.provedor:SIMULADO}") String nomeAtivo) {
        this.porNome = provedores.stream().collect(Collectors.toMap(p -> p.nome().toUpperCase(Locale.ROOT),
                Function.identity()));
        this.ativo = porNome(nomeAtivo).orElseThrow(() -> new IllegalStateException(
                "Provedor de pagamento '" + nomeAtivo + "' não disponível. Disponíveis: " + porNome.keySet()));
        if (ProvedorSimulado.NOME.equals(ativo.nome()))
            log.warn("Provedor de pagamento SIMULADO ativo: as cobranças são fictícias e nenhum valor é recebido.");
    }

    public ProvedorPagamento ativo() {
        return ativo;
    }

    public Optional<ProvedorPagamento> porNome(String nome) {
        return nome == null ? Optional.empty() : Optional.ofNullable(porNome.get(nome.toUpperCase(Locale.ROOT)));
    }

    public ProvedorPagamento daCobranca(String provedor) {
        return porNome(provedor).orElseThrow(() -> new IllegalStateException(
                "A cobrança foi criada no provedor '" + provedor + "', que não está mais configurado."));
    }
}
