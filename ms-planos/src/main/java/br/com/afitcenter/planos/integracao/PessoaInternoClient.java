package br.com.afitcenter.planos.integracao;

import br.com.afitcenter.planos.model.dto.Dtos.PessoaInternoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Rota interna do ms-pessoa com os dados do pagador: o id do nutricionista é o id da pessoa (token interno adicionado
 * pelo interceptor do core-web).
 */
@FeignClient(name = "ms-pessoa", contextId = "pessoaInternoClient", path = "/internal/pessoas")
public interface PessoaInternoClient {

    @GetMapping("/{id}")
    PessoaInternoResponse buscar(@PathVariable("id") UUID id);
}
