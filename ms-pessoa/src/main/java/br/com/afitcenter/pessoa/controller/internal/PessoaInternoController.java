package br.com.afitcenter.pessoa.controller.internal;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.pessoa.model.dto.Dtos.PessoaInternoResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.PessoaRegistradaResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.RegistrarPessoaRequest;
import br.com.afitcenter.pessoa.service.PessoaService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Rotas chamadas só por outros serviços (token interno). O Gateway as bloqueia. */
@Hidden
@RestController
@RequestMapping("/internal/pessoas")
@RequiredArgsConstructor
public class PessoaInternoController {

    private final PessoaService pessoaService;

    /** Cadastro do nutricionista (ms-nutricionista): cria a pessoa ou acrescenta o papel à conta do e-mail. */
    @PostMapping
    public ResponseEntity<PessoaRegistradaResponse> registrar(@Valid @RequestBody RegistrarPessoaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pessoaService.registrar(dados));
    }

    /** Compensação do cadastro: tira o papel e, se a pessoa ficar sem nenhum, remove a conta. Idempotente. */
    @DeleteMapping("/{id}/papeis/{papel}")
    public ResponseEntity<Void> removerPapel(@PathVariable UUID id, @PathVariable Papel papel) {
        pessoaService.removerPapel(id, papel);
        return ResponseEntity.noContent().build();
    }

    /** Pagador das cobranças da assinatura (ms-planos): nome, CPF e e-mail. */
    @GetMapping("/{id}")
    public PessoaInternoResponse buscar(@PathVariable UUID id) {
        return pessoaService.buscarInterno(id);
    }
}
