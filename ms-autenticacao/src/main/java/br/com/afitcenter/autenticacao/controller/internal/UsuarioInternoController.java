package br.com.afitcenter.autenticacao.controller.internal;

import br.com.afitcenter.autenticacao.model.dto.Dtos.CriarUsuarioRequest;
import br.com.afitcenter.autenticacao.model.dto.Dtos.UsuarioCriadoResponse;
import br.com.afitcenter.autenticacao.service.UsuarioService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Rotas chamadas só por outros serviços (token interno). O Gateway as bloqueia. */
@Hidden
@RestController
@RequestMapping("/internal/usuarios")
@RequiredArgsConstructor
public class UsuarioInternoController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioCriadoResponse> criar(@Valid @RequestBody CriarUsuarioRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new UsuarioCriadoResponse(usuarioService.criar(dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        usuarioService.remover(id);
        return ResponseEntity.noContent().build();
    }
}
