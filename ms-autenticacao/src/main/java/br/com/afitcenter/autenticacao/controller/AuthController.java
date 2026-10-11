package br.com.afitcenter.autenticacao.controller;

import br.com.afitcenter.autenticacao.model.dto.Dtos.LoginRequest;
import br.com.afitcenter.autenticacao.model.dto.Dtos.TokenResponse;
import br.com.afitcenter.autenticacao.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login e emissão do token de acesso.")
public class AuthController {

    private final LoginService loginService;

    @PostMapping("/login")
    @Operation(summary = "Efetuar login",
            description = "Devolve um JWT ES256 com o usuário (sub) e os papéis (roles). Sem refresh token: "
                    + "quando expirar, faça login de novo.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado"),
                    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos"),
                    @ApiResponse(responseCode = "403", description = "Usuário inativo ou bloqueado"),
                    @ApiResponse(responseCode = "429", description = "Muitas tentativas com senha incorreta")
            })
    public TokenResponse login(@Valid @RequestBody LoginRequest dados) {
        return loginService.autenticar(dados);
    }
}
