package br.com.afitcenter.nutricionista.model.dto;

import br.com.afitcenter.core.base.validacao.CpfValido;
import br.com.afitcenter.nutricionista.model.entity.Consultorio;
import br.com.afitcenter.nutricionista.model.entity.Endereco;
import br.com.afitcenter.nutricionista.model.entity.Nutricionista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-nutricionista. */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- cadastro público

    public record CadastroRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @CpfValido String cpf,
            @NotNull @Min(1) @Max(99) Integer crnRegiao,
            @NotBlank @Pattern(regexp = "\\d{1,20}", message = "Informe só os números do CRN.") String crnNumero,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
            @Size(max = 20) String telefone,
            @AssertTrue(message = "É necessário aceitar os termos de uso e a política de privacidade.") boolean aceiteTermos,
            @NotBlank @Size(max = 20) String versaoTermos
    ) {
    }

    public record CadastroResponse(UUID nutricionistaId) {
    }

    // ---------------------------------------------------------------- perfil

    public record EnderecoDto(
            @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido.") String cep,
            @Size(max = 200) String logradouro,
            @Size(max = 20) String numero,
            @Size(max = 100) String complemento,
            @Size(max = 100) String bairro,
            @Size(max = 100) String cidade,
            @Pattern(regexp = "[A-Z]{2}", message = "UF inválida.") String uf
    ) {

        public Endereco paraEntidade() {
            return new Endereco(cep == null ? null : cep.replace("-", ""), logradouro, numero, complemento,
                    bairro, cidade, uf);
        }

        public static EnderecoDto de(Endereco endereco) {
            if (endereco == null) return null;
            return new EnderecoDto(endereco.getCep(), endereco.getLogradouro(), endereco.getNumero(),
                    endereco.getComplemento(), endereco.getBairro(), endereco.getCidade(), endereco.getUf());
        }
    }

    public record AtualizarPerfilRequest(
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 20) String telefone,
            @Size(max = 500) String especialidades,
            @Size(max = 500) String assinaturaUrl,
            @Size(max = 500) String logoUrl,
            @Valid EnderecoDto endereco
    ) {
    }

    public record NutricionistaResponse(
            UUID id,
            String nome,
            String cpf,
            Integer crnRegiao,
            String crnNumero,
            String crn,
            String email,
            String telefone,
            String especialidades,
            String assinaturaUrl,
            String logoUrl,
            EnderecoDto endereco
    ) {

        public static NutricionistaResponse de(Nutricionista n) {
            return new NutricionistaResponse(n.getId(), n.getNome(), n.getCpf(), n.getCrnRegiao(), n.getCrnNumero(),
                    n.crnFormatado(), n.getEmail(), n.getTelefone(), n.getEspecialidades(), n.getAssinaturaUrl(),
                    n.getLogoUrl(), EnderecoDto.de(n.getEndereco()));
        }
    }

    // ---------------------------------------------------------------- consultórios

    public record ConsultorioRequest(
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 20) String telefone,
            @Valid EnderecoDto endereco
    ) {
    }

    public record ConsultorioResponse(UUID id, String nome, String telefone, EnderecoDto endereco, boolean ativo) {

        public static ConsultorioResponse de(Consultorio c) {
            return new ConsultorioResponse(c.getId(), c.getNome(), c.getTelefone(), EnderecoDto.de(c.getEndereco()),
                    c.isAtivo());
        }
    }

    /** Formato de {@code GET /internal/consultorios/{id}}, usado na réplica do ms-consulta. */
    public record ConsultorioInternoResponse(UUID id, UUID nutricionistaId, String nome, boolean ativo, long versao) {

        public static ConsultorioInternoResponse de(Consultorio c) {
            return new ConsultorioInternoResponse(c.getId(), c.getNutricionistaId(), c.getNome(), c.isAtivo(),
                    c.getVersao());
        }
    }

    // ---------------------------------------------------------------- contrato com o ms-autenticacao

    public record CriarUsuarioRequest(String email, String senha, String perfil, List<AceiteRequest> aceites, String ip) {
    }

    public record AceiteRequest(String tipo, String versao) {
    }

    public record UsuarioCriadoResponse(UUID id) {
    }
}
