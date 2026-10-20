package br.com.afitcenter.pessoa.controller;

import br.com.afitcenter.pessoa.model.dto.Dtos.CadastroPacienteRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.CadastroResponse;
import br.com.afitcenter.pessoa.service.PessoaService;
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
@Tag(name = "Cadastro", description = "Cadastro público do paciente. O do nutricionista fica no ms-nutricionista.")
public class CadastroController {

    private final PessoaService pessoaService;

    @PostMapping("/paciente")
    @Operation(summary = "Cadastrar paciente",
            description = "Cria a conta de acesso do paciente. Se o e-mail já tem conta e a senha confere, o papel de "
                    + "paciente é acrescentado a ela. Depois do login, ligue a conta à ficha com o código de acesso "
                    + "gerado pelo nutricionista (ms-paciente, POST /v1/portal/vinculos).",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Conta criada (ou papel acrescentado)"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "409", description = "E-mail com outra senha, CPF já cadastrado ou conta já é de paciente"),
                    @ApiResponse(responseCode = "429", description = "Muitas tentativas com senha incorreta")
            })
    public ResponseEntity<CadastroResponse> cadastrarPaciente(@Valid @RequestBody CadastroPacienteRequest dados,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pessoaService.cadastrarPaciente(dados, request.getRemoteAddr()));
    }
}
