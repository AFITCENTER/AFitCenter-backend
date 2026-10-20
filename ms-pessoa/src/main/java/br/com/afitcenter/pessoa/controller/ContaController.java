package br.com.afitcenter.pessoa.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.pessoa.model.dto.Dtos.AtualizarContaRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.ConfirmarEmailRequest;
import br.com.afitcenter.pessoa.model.dto.Dtos.ContaResponse;
import br.com.afitcenter.pessoa.model.dto.Dtos.TrocaSenhaRequest;
import br.com.afitcenter.pessoa.service.ConfirmacaoEmailService;
import br.com.afitcenter.pessoa.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/conta")
@RequiredArgsConstructor
@Tag(name = "Conta", description = "Dados pessoais e de acesso de quem está logado (qualquer papel). "
        + "A confirmação do e-mail é pública: o token do link identifica a conta.")
public class ContaController {

    private final PessoaService pessoaService;
    private final ConfirmacaoEmailService confirmacaoEmail;

    @GetMapping
    @Operation(summary = "Minha conta",
            description = "Nome, CPF, e-mail (e se já foi confirmado), telefone, papéis e último acesso.")
    public ContaResponse minhaConta() {
        return pessoaService.conta(UsuarioAutenticado.usuarioId());
    }

    @PutMapping
    @Operation(summary = "Atualizar nome e telefone", description = "E-mail (o login) e CPF não mudam por aqui.")
    public ContaResponse atualizar(@Valid @RequestBody AtualizarContaRequest dados) {
        return pessoaService.atualizarConta(UsuarioAutenticado.usuarioId(), dados);
    }

    @PutMapping("/senha")
    @Operation(summary = "Trocar a senha (exige a senha atual)")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocaSenhaRequest dados) {
        pessoaService.trocarSenha(UsuarioAutenticado.usuarioId(), dados);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/confirmacao")
    @Operation(summary = "Confirmar o e-mail com o token do link (público)",
            description = "O token vem do fragmento do link enviado no cadastro ({url-frontend}/confirmar-email#token=...). "
                    + "Vale 48 horas e uma vez só. O login não exige o e-mail confirmado.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "E-mail confirmado"),
                    @ApiResponse(responseCode = "400", description = "Token ausente"),
                    @ApiResponse(responseCode = "422", description = "Link inválido, já usado ou vencido")
            })
    public ResponseEntity<Void> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest dados) {
        confirmacaoEmail.confirmar(dados);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/confirmacao/reenvio")
    @Operation(summary = "Reenviar o link de confirmação do e-mail",
            description = "Envia um link novo para o e-mail da conta logada; o anterior deixa de valer.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "E-mail a caminho"),
                    @ApiResponse(responseCode = "409", description = "O e-mail já está confirmado"),
                    @ApiResponse(responseCode = "429", description = "Muitos pedidos para este e-mail")
            })
    public ResponseEntity<Void> reenviarConfirmacao() {
        confirmacaoEmail.reenviar(UsuarioAutenticado.usuarioId());
        return ResponseEntity.accepted().build();
    }
}
