package br.com.afitcenter.autenticacao.model.dto;

import br.com.afitcenter.autenticacao.model.entity.Usuario;
import br.com.afitcenter.autenticacao.model.enums.StatusUsuario;
import br.com.afitcenter.autenticacao.model.enums.TipoTermo;
import br.com.afitcenter.core.base.security.Papel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-autenticacao. */
public final class Dtos {

    private Dtos() {
    }

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

    /** Senha entre 8 e 72 caracteres (limite do bcrypt). */
    public record CriarUsuarioRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
            @NotNull Papel perfil,
            @NotEmpty(message = "É obrigatório registrar o aceite dos termos.") List<@Valid AceiteRequest> aceites,
            @Size(max = 45) String ip
    ) {
    }

    public record AceiteRequest(@NotNull TipoTermo tipo, @NotBlank @Size(max = 20) String versao) {
    }

    public record UsuarioCriadoResponse(UUID id) {
    }

    public record ContaResponse(UUID id, String email, Papel perfil, StatusUsuario status, Instant ultimoAcesso) {

        public static ContaResponse de(Usuario usuario) {
            return new ContaResponse(usuario.getId(), usuario.getEmail(), usuario.getPerfil(), usuario.getStatus(),
                    usuario.getUltimoAcesso());
        }
    }

    public record TrocaSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String novaSenha
    ) {
    }
}
