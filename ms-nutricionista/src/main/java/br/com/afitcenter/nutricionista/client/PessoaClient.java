package br.com.afitcenter.nutricionista.client;

import br.com.afitcenter.nutricionista.model.dto.Dtos.PessoaRegistradaResponse;
import br.com.afitcenter.nutricionista.model.dto.Dtos.RegistrarPessoaRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** Rotas internas do ms-pessoa (token interno adicionado pelo interceptor do core-web). */
@FeignClient(name = "ms-pessoa", contextId = "pessoaClient", path = "/internal/pessoas")
public interface PessoaClient {

    /**
     * Cria a pessoa ou, se o e-mail já tem conta e a senha confere, acrescenta o papel a ela. 409 com o motivo
     * (e-mail com outra senha, CPF de outra conta, conta já cadastrada com o papel); 429 depois de muitas senhas
     * erradas.
     */
    @PostMapping
    PessoaRegistradaResponse registrar(@RequestBody RegistrarPessoaRequest dados);

    /** Compensação do cadastro: tira o papel e, se a pessoa ficar sem nenhum, remove a conta. Idempotente. */
    @DeleteMapping("/{id}/papeis/{papel}")
    void removerPapel(@PathVariable("id") UUID id, @PathVariable("papel") String papel);
}
