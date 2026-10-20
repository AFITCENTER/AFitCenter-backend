package br.com.afitcenter.pessoa.model.dto;

import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.base.validacao.CpfValido;
import br.com.afitcenter.pessoa.model.entity.Pessoa;
import br.com.afitcenter.pessoa.model.enums.StatusPessoa;
import br.com.afitcenter.pessoa.model.enums.TipoTermo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-pessoa. */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- login

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String senha
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UUID usuarioId,
            List<String> papeis
    ) {
    }

    // ---------------------------------------------------------------- cadastro público do paciente

    /**
     * O paciente cria a própria conta. Se o e-mail já tem conta (a de uma nutricionista, por exemplo) e a senha
     * confere, o papel de paciente é acrescentado a ela. Senha entre 8 e 72 caracteres (limite do bcrypt).
     */
    public record CadastroPacienteRequest(
            @NotBlank @Size(max = 150) String nome,
            @CpfValido String cpf,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
            @Size(max = 20) String telefone,
            @AssertTrue(message = "É necessário aceitar os termos de uso e a política de privacidade.") boolean aceiteTermos,
            @NotBlank @Size(max = 20) String versaoTermos
    ) {
    }

    public record CadastroResponse(UUID pessoaId) {
    }

    // ---------------------------------------------------------------- conta

    /** {@code emailConfirmado}: a pessoa já abriu um link enviado ao e-mail. O login não exige (v1). */
    public record ContaResponse(
            UUID id,
            String nome,
            String cpf,
            String email,
            boolean emailConfirmado,
            String telefone,
            List<String> papeis,
            StatusPessoa status,
            Instant ultimoAcesso
    ) {

        public static ContaResponse de(Pessoa pessoa) {
            return new ContaResponse(pessoa.getId(), pessoa.getNome(), pessoa.getCpf(), pessoa.getEmail(),
                    pessoa.isEmailConfirmado(), pessoa.getTelefone(), Papel.nomes(pessoa.getPapeis()),
                    pessoa.getStatus(), pessoa.getUltimoAcesso());
        }
    }

    /** E-mail (o login) e CPF não mudam por aqui. */
    public record AtualizarContaRequest(
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 20) String telefone
    ) {
    }

    public record TrocaSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String novaSenha
    ) {
    }

    // ---------------------------------------------------------------- links por e-mail

    public record EsqueciSenhaRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    /** O token vem do link enviado por e-mail ({@code /redefinir-senha#token=...}). */
    public record RedefinirSenhaRequest(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String novaSenha
    ) {
    }

    /** O token vem do link enviado por e-mail ({@code /confirmar-email#token=...}). */
    public record ConfirmarEmailRequest(@NotBlank @Size(max = 100) String token) {
    }

    // ---------------------------------------------------------------- rotas internas

    /**
     * Cadastro feito por outro serviço (o do nutricionista, no ms-nutricionista). Mesma regra do cadastro do
     * paciente: e-mail novo cria a pessoa; e-mail com conta e senha certa acrescenta o papel.
     */
    public record RegistrarPessoaRequest(
            @NotBlank @Size(max = 150) String nome,
            @CpfValido String cpf,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
            @Size(max = 20) String telefone,
            @NotNull Papel papel,
            @NotEmpty(message = "É obrigatório registrar o aceite dos termos.") List<@Valid AceiteRequest> aceites,
            @Size(max = 45) String ip
    ) {
    }

    public record AceiteRequest(@NotNull TipoTermo tipo, @NotBlank @Size(max = 20) String versao) {
    }

    /** {@code criada = false}: o papel foi acrescentado a uma conta que já existia. */
    public record PessoaRegistradaResponse(UUID id, boolean criada) {
    }

    /** Formato de {@code GET /internal/pessoas/{id}}: o pagador das cobranças da assinatura (ms-planos). */
    public record PessoaInternoResponse(UUID id, String nome, String cpf, String email) {

        public static PessoaInternoResponse de(Pessoa pessoa) {
            return new PessoaInternoResponse(pessoa.getId(), pessoa.getNome(), pessoa.getCpf(), pessoa.getEmail());
        }
    }
}
