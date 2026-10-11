package br.com.afitcenter.nutricionista.client;

import br.com.afitcenter.nutricionista.model.dto.Dtos.CriarUsuarioRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.UsuarioCriadoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** Rotas internas do ms-autenticacao (token interno adicionado pelo interceptor do core-web). */
@FeignClient(name = "ms-autenticacao", contextId = "autenticacaoClient", path = "/internal/usuarios")
public interface AutenticacaoClient {

    /** 409 quando já existe conta com o e-mail. */
    @PostMapping
    UsuarioCriadoResponse criar(@RequestBody CriarUsuarioRequest dados);

    /** Compensação do cadastro. Idempotente. */
    @DeleteMapping("/{id}")
    void remover(@PathVariable("id") UUID id);
}
