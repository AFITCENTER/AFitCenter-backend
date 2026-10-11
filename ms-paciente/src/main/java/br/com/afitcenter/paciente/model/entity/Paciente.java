package br.com.afitcenter.paciente.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.paciente.model.enums.Enums.NivelAtividade;
import br.com.afitcenter.paciente.model.enums.Enums.Sexo;
import br.com.afitcenter.paciente.model.enums.Enums.StatusPaciente;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ficha do paciente. Cada nutricionista tem a própria ficha: a mesma pessoa atendida por dois
 * nutricionistas tem duas fichas independentes (CPF único por nutricionista).
 */
@Getter
@Entity
@Table(name = "paciente")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Paciente extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nutricionista_id", nullable = false, updatable = false)
    private UUID nutricionistaId;

    /** Login do futuro portal do paciente (nulo na v1). */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "nome_social")
    private String nomeSocial;

    @Column(length = 11)
    private String cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sexo sexo;

    private String ocupacao;

    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_atividade")
    private NivelAtividade nivelAtividade;

    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPaciente status;

    @Embedded
    private Endereco endereco;

    @ElementCollection
    @CollectionTable(name = "paciente_contato", joinColumns = @JoinColumn(name = "paciente_id"))
    @OrderColumn(name = "ordem")
    private List<Contato> contatos = new ArrayList<>();

    /** Bloqueio otimista e ordem dos eventos para as réplicas. */
    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "deletado_em")
    private Instant deletadoEm;

    public Paciente(UUID nutricionistaId) {
        this.nutricionistaId = nutricionistaId;
        this.status = StatusPaciente.ATIVO;
    }

    public void preencher(String nome, String nomeSocial, String cpf, LocalDate dataNascimento, Sexo sexo,
                          String ocupacao, String objetivo, NivelAtividade nivelAtividade, String observacoes,
                          StatusPaciente status, Endereco endereco, List<Contato> contatos) {
        this.nome = nome;
        this.nomeSocial = nomeSocial;
        this.cpf = cpf;
        this.dataNascimento = dataNascimento;
        this.sexo = sexo;
        this.ocupacao = ocupacao;
        this.objetivo = objetivo;
        this.nivelAtividade = nivelAtividade;
        this.observacoes = observacoes;
        if (status != null) this.status = status;
        this.endereco = endereco;
        this.contatos.clear();
        this.contatos.addAll(contatos);
    }

    /** Exclusão lógica: prontuário não se apaga (prazo legal de guarda). */
    public void excluir() {
        this.deletadoEm = Instant.now();
    }

    public boolean isExcluido() {
        return deletadoEm != null;
    }

    /** Nome social quando houver; é o que aparece em listas e na agenda. */
    public String nomeExibicao() {
        return nomeSocial != null && !nomeSocial.isBlank() ? nomeSocial : nome;
    }

    public int idade() {
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }
}
