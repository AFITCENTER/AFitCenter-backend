package br.com.afitcenter.paciente.model.dto;

import br.com.afitcenter.core.base.validacao.CpfValido;
import br.com.afitcenter.paciente.model.entity.Condicao;
import br.com.afitcenter.paciente.model.entity.Contato;
import br.com.afitcenter.paciente.model.entity.Endereco;
import br.com.afitcenter.paciente.model.entity.Paciente;
import br.com.afitcenter.paciente.model.entity.PacienteCondicao;
import br.com.afitcenter.paciente.model.entity.PacienteMedicamento;
import br.com.afitcenter.paciente.model.entity.TipoCondicao;
import br.com.afitcenter.paciente.model.enums.Enums.Gravidade;
import br.com.afitcenter.paciente.model.enums.Enums.NivelAtividade;
import br.com.afitcenter.paciente.model.enums.Enums.Sexo;
import br.com.afitcenter.paciente.model.enums.Enums.StatusPaciente;
import br.com.afitcenter.paciente.model.enums.Enums.TipoContato;
import br.com.afitcenter.paciente.model.enums.Enums.TipoMedicamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do ms-paciente. */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- endereço e contatos

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

        public static EnderecoDto de(Endereco e) {
            return e == null ? null : new EnderecoDto(e.getCep(), e.getLogradouro(), e.getNumero(),
                    e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
        }
    }

    public record ContatoDto(
            @NotNull TipoContato tipo,
            @NotBlank @Size(max = 150) String valor,
            boolean principal,
            @Size(max = 150) String observacao
    ) {

        public Contato paraEntidade() {
            return new Contato(tipo, valor.trim(), principal, observacao);
        }

        public static ContatoDto de(Contato c) {
            return new ContatoDto(c.getTipo(), c.getValor(), c.isPrincipal(), c.getObservacao());
        }
    }

    // ---------------------------------------------------------------- paciente

    public record PacienteRequest(
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 150) String nomeSocial,
            @CpfValido String cpf,
            @NotNull @Past LocalDate dataNascimento,
            @NotNull Sexo sexo,
            @Size(max = 150) String ocupacao,
            @Size(max = 500) String objetivo,
            NivelAtividade nivelAtividade,
            @Size(max = 4000) String observacoes,
            StatusPaciente status,
            @Valid EnderecoDto endereco,
            @Size(max = 10) List<@Valid ContatoDto> contatos
    ) {
    }

    public record PacienteResponse(
            UUID id,
            String nome,
            String nomeSocial,
            String nomeExibicao,
            String cpf,
            LocalDate dataNascimento,
            int idade,
            Sexo sexo,
            String ocupacao,
            String objetivo,
            NivelAtividade nivelAtividade,
            String observacoes,
            StatusPaciente status,
            EnderecoDto endereco,
            List<ContatoDto> contatos,
            boolean acessoAtivo,
            Instant acessoDesde,
            Instant criadoEm,
            Instant atualizadoEm
    ) {

        public static PacienteResponse de(Paciente p) {
            return new PacienteResponse(p.getId(), p.getNome(), p.getNomeSocial(), p.nomeExibicao(), p.getCpf(),
                    p.getDataNascimento(), p.idade(), p.getSexo(), p.getOcupacao(), p.getObjetivo(),
                    p.getNivelAtividade(), p.getObservacoes(), p.getStatus(), EnderecoDto.de(p.getEndereco()),
                    p.getContatos().stream().map(ContatoDto::de).toList(), p.getPessoaId() != null,
                    p.getPessoaVinculadaEm(), p.getCriadoEm(), p.getAtualizadoEm());
        }
    }

    public record PacienteResumoResponse(
            UUID id,
            String nomeExibicao,
            String nome,
            int idade,
            Sexo sexo,
            StatusPaciente status,
            String contatoPrincipal
    ) {

        public static PacienteResumoResponse de(Paciente p) {
            String contato = p.getContatos().stream().filter(Contato::isPrincipal).findFirst()
                    .or(() -> p.getContatos().stream().findFirst())
                    .map(Contato::getValor).orElse(null);
            return new PacienteResumoResponse(p.getId(), p.nomeExibicao(), p.getNome(), p.idade(), p.getSexo(),
                    p.getStatus(), contato);
        }
    }

    /** Formato de {@code GET /internal/pacientes/{id}}: o mesmo {@code PacienteRef} do core-paciente. */
    public record PacienteInternoResponse(
            UUID id,
            UUID nutricionistaId,
            String nome,
            String sexo,
            LocalDate dataNascimento,
            String status,
            boolean excluido,
            long versao
    ) {

        public static PacienteInternoResponse de(Paciente p) {
            return new PacienteInternoResponse(p.getId(), p.getNutricionistaId(), p.nomeExibicao(), p.getSexo().name(),
                    p.getDataNascimento(), p.getStatus().name(), p.isExcluido(), p.getVersao());
        }
    }

    // ---------------------------------------------------------------- acesso do paciente ao portal

    /**
     * Código para o nutricionista entregar ao paciente. Aparece só nesta resposta: o banco guarda o hash, então um
     * código perdido não é recuperado, e gerar outro invalida o anterior.
     */
    public record CodigoAcessoResponse(String codigo, Instant expiraEm) {
    }

    public record VinculoRequest(@NotBlank @Size(max = 20) String codigo) {
    }

    /**
     * Ficha vista pelo próprio paciente no portal. Só identificação: anotações e dados clínicos do nutricionista
     * (observações, condições, medicamentos) não aparecem aqui.
     */
    public record FichaPortalResponse(
            UUID id,
            UUID nutricionistaId,
            String nomeExibicao,
            StatusPaciente status,
            Instant vinculadaEm
    ) {

        public static FichaPortalResponse de(Paciente p) {
            return new FichaPortalResponse(p.getId(), p.getNutricionistaId(), p.nomeExibicao(), p.getStatus(),
                    p.getPessoaVinculadaEm());
        }
    }

    // ---------------------------------------------------------------- catálogo de condições

    public record TipoCondicaoResponse(Integer id, String nome) {

        public static TipoCondicaoResponse de(TipoCondicao t) {
            return new TipoCondicaoResponse(t.getId(), t.getNome());
        }
    }

    public record CondicaoRequest(
            @NotNull Integer tipoCondicaoId,
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 500) String descricao
    ) {
    }

    public record CondicaoResponse(Integer id, Integer tipoId, String tipo, String nome, String descricao, boolean propria) {

        public static CondicaoResponse de(Condicao c) {
            return new CondicaoResponse(c.getId(), c.getTipo().getId(), c.getTipo().getNome(), c.getNome(),
                    c.getDescricao(), !c.isDoSistema());
        }
    }

    // ---------------------------------------------------------------- condições do paciente

    public record PacienteCondicaoRequest(
            @NotNull Integer condicaoId,
            Gravidade gravidade,
            @PastOrPresent LocalDate desde,
            Boolean ativa,
            @Size(max = 500) String observacao
    ) {
    }

    public record AtualizarPacienteCondicaoRequest(
            Gravidade gravidade,
            @PastOrPresent LocalDate desde,
            @NotNull Boolean ativa,
            @Size(max = 500) String observacao
    ) {
    }

    public record PacienteCondicaoResponse(
            UUID id,
            CondicaoResponse condicao,
            Gravidade gravidade,
            LocalDate desde,
            boolean ativa,
            String observacao
    ) {

        public static PacienteCondicaoResponse de(PacienteCondicao pc) {
            return new PacienteCondicaoResponse(pc.getId(), CondicaoResponse.de(pc.getCondicao()), pc.getGravidade(),
                    pc.getDesde(), pc.isAtiva(), pc.getObservacao());
        }
    }

    // ---------------------------------------------------------------- medicamentos e suplementos

    public record MedicamentoRequest(
            @NotNull TipoMedicamento tipo,
            @NotBlank @Size(max = 150) String nome,
            @Size(max = 100) String dosagem,
            @Size(max = 100) String frequencia,
            LocalDate inicio,
            LocalDate fim,
            @Size(max = 500) String observacao
    ) {

        @AssertTrue(message = "O fim não pode ser anterior ao início.")
        public boolean isPeriodoValido() {
            return inicio == null || fim == null || !fim.isBefore(inicio);
        }
    }

    public record MedicamentoResponse(
            UUID id,
            TipoMedicamento tipo,
            String nome,
            String dosagem,
            String frequencia,
            LocalDate inicio,
            LocalDate fim,
            String observacao
    ) {

        public static MedicamentoResponse de(PacienteMedicamento m) {
            return new MedicamentoResponse(m.getId(), m.getTipo(), m.getNome(), m.getDosagem(), m.getFrequencia(),
                    m.getInicio(), m.getFim(), m.getObservacao());
        }
    }
}
