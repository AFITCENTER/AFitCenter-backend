package br.com.afitcenter.pessoa.controller;

import br.com.afitcenter.pessoa.model.dto.Dtos.EsqueciSenhaRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.RedefinirSenhaRequest;
import br.com.afitcenter.pessoa.service.RecuperacaoSenhaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/senha")
@RequiredArgsConstructor
@Tag(name = "Senha", description = "Recuperação de senha por e-mail (público). Para trocar a senha logado, use PUT /v1/conta/senha.")
public class SenhaController {

    private final RecuperacaoSenhaService recuperacao;

    @PostMapping("/esqueci")
    @Operation(summary = "Pedir o link de redefinição de senha",
            description = "Se o e-mail tem uma conta ativa, envia um link de uso único que vale 30 minutos "
                    + "({url-frontend}/redefinir-senha#token=...). Pedir outro invalida o anterior. A resposta é "
                    + "sempre 202, sem corpo, exista ou não a conta.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Pedido aceito"),
                    @ApiResponse(responseCode = "400", description = "E-mail inválido"),
                    @ApiResponse(responseCode = "429", description = "Muitos pedidos para este e-mail")
            })
    public ResponseEntity<Void> esqueci(@Valid @RequestBody EsqueciSenhaRequest dados) {
        recuperacao.solicitar(dados);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/redefinir")
    @Operation(summary = "Redefinir a senha com o token do link",
            description = "O token vem do fragmento do link (#token=...). Depois disso a senha antiga deixa de "
                    + "valer, o link não pode ser usado de novo e o e-mail da conta fica confirmado.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Senha redefinida"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "422", description = "Link inválido, já usado ou vencido")
            })
    public ResponseEntity<Void> redefinir(@Valid @RequestBody RedefinirSenhaRequest dados) {
        recuperacao.redefinir(dados);
        return ResponseEntity.noContent().build();
    }
}
