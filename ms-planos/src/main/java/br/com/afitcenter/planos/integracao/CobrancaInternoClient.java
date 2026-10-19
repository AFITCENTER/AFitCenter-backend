package br.com.afitcenter.planos.integracao;

import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.CriarCobrancaRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** Rotas internas do ms-pagamentos (token interno adicionado pelo interceptor do core-web). */
@FeignClient(name = "ms-pagamentos", contextId = "cobrancaInternoClient", path = "/internal/cobrancas")
public interface CobrancaInternoClient {

    /** Idempotente pela {@code chaveIdempotencia}: repetir devolve a mesma cobrança. */
    @PostMapping
    CobrancaResponse criar(@RequestBody CriarCobrancaRequest dados);

    /** Idempotente para cobranças não pagas; 409 se a cobrança já foi paga. */
    @PostMapping("/{id}/cancelamento")
    void cancelar(@PathVariable("id") UUID id);
}
