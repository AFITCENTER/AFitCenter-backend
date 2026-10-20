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

    /**
     * Nome, CPF, e-mail, senha e telefone vão para o ms-pessoa (dono dos dados pessoais e do acesso); CRN fica aqui.
     * Quem já tem conta (como paciente, por exemplo) informa o mesmo e-mail e senha, e a conta ganha o papel de
     * nutricionista.
     */
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

    /** Nome e telefone mudam no ms-pessoa ({@code PUT /v1/conta}); CPF, CRN e e-mail não mudam. */
    public record AtualizarPerfilRequest(
            @Size(max = 500) String especialidades,
            @Size(max = 500) String assinaturaUrl,
            @Size(max = 500) String logoUrl,
            @Valid EnderecoDto endereco
    ) {
    }

    /** Dados profissionais. Os pessoais (nome, CPF, e-mail, telefone) estão em {@code GET /ms-pessoa/v1/conta}. */
    public record NutricionistaResponse(
            UUID id,
            Integer crnRegiao,
            String crnNumero,
            String crn,
            String especialidades,
            String assinaturaUrl,
            String logoUrl,
            EnderecoDto endereco
    ) {

        public static NutricionistaResponse de(Nutricionista n) {
            return new NutricionistaResponse(n.getId(), n.getCrnRegiao(), n.getCrnNumero(), n.crnFormatado(),
                    n.getEspecialidades(), n.getAssinaturaUrl(), n.getLogoUrl(), EnderecoDto.de(n.getEndereco()));
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

    // ---------------------------------------------------------------- contrato com o ms-pessoa

    public record RegistrarPessoaRequest(String nome, String cpf, String email, String senha, String telefone,
                                         String papel, List<AceiteRequest> aceites, String ip) {
    }

    public record AceiteRequest(String tipo, String versao) {
    }

    /** {@code criada = false}: o e-mail já tinha conta e ela ganhou o papel de nutricionista. */
    public record PessoaRegistradaResponse(UUID id, boolean criada) {
    }
}
