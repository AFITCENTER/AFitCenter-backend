package br.com.afitcenter.nutricionista.controller;

import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.CadastroResponse;
import br.com.afitcenter.nutricionista.service.CadastroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cadastros")
@RequiredArgsConstructor
@Tag(name = "Cadastro", description = "Cadastro público de nutricionistas.")
public class CadastroController {

    private final CadastroService cadastroService;

    @PostMapping
    @Operation(summary = "Cadastrar nutricionista",
            description = "Cria a conta de acesso no ms-pessoa e o perfil profissional. Quem já tem conta (como paciente) "
                    + "informa o mesmo e-mail e senha, e a conta ganha o papel de nutricionista. Depois, faça login no "
                    + "ms-pessoa.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Cadastro criado"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "409", description = "CRN ou CPF já cadastrado, ou e-mail de outra conta com outra senha"),
                    @ApiResponse(responseCode = "429", description = "Muitas tentativas com senha incorreta")
            })
    public ResponseEntity<CadastroResponse> cadastrar(@Valid @RequestBody CadastroRequest dados,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastroService.cadastrar(dados, request.getRemoteAddr()));
    }
}
