package br.com.afitcenter.autenticacao.controller;

import br.com.afitcenter.autenticacao.model.dto.Dtos.ContaResponse;
import br.com.afitcenter.autenticacao.model.dto.Dtos.TrocaSenhaRequest;
import br.com.afitcenter.autenticacao.service.UsuarioService;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/conta")
@RequiredArgsConstructor
@Tag(name = "Conta", description = "Dados de acesso do usuário logado.")
public class ContaController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Dados da conta do usuário logado")
    public ContaResponse minhaConta() {
        return usuarioService.conta(UsuarioAutenticado.usuarioId());
    }

    @PutMapping("/senha")
    @Operation(summary = "Trocar a senha (exige a senha atual)")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocaSenhaRequest dados) {
        usuarioService.trocarSenha(UsuarioAutenticado.usuarioId(), dados);
        return ResponseEntity.noContent().build();
    }
}
