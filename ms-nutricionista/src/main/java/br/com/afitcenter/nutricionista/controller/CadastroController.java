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
            description = "Cria a conta de acesso e o perfil profissional. Depois, faça login no ms-auth.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Cadastro criado"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "409", description = "E-mail, CPF ou CRN já cadastrado")
            })
    public ResponseEntity<CadastroResponse> cadastrar(@Valid @RequestBody CadastroRequest dados,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastroService.cadastrar(dados, request.getRemoteAddr()));
    }
}
