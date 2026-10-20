package br.com.afitcenter.pessoa.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.pessoa.model.enums.StatusPessoa;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quem acessa o sistema: dados pessoais, credencial e papéis. Uma pessoa pode ter mais de um papel (a nutricionista
 * que também é paciente de outro nutricionista) e entra sempre com o mesmo e-mail e senha.
 *
 * <p>O id é o {@code sub} do JWT. Para o nutricionista, é também o id do perfil no ms-nutricionista e o tenant de
 * todos os dados dele; para o paciente, é o {@code pessoa_id} das fichas ligadas à conta no ms-paciente.</p>
 */
@Getter
@Entity
@Table(name = "pessoa")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pessoa extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    /** Opcional para o paciente. O cadastro do nutricionista exige: o provedor de pagamento pede o CPF do pagador. */
    @Column(length = 11)
    private String cpf;

    /** Login. Sempre minúsculo e sem espaços nas pontas (índice único). */
    @Column(nullable = false)
    private String email;

    /** Quando a pessoa abriu um link enviado ao e-mail (confirmação ou redefinição de senha). O login não exige. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    @Column(length = 20)
    private String telefone;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPessoa status;

    @Column(name = "ultimo_acesso")
    private Instant ultimoAcesso;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pessoa_papel", joinColumns = @JoinColumn(name = "pessoa_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false)
    private Set<Papel> papeis = new HashSet<>();

    public Pessoa(String nome, String cpf, String email, String telefone, String senhaHash, Papel papel) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.telefone = telefone;
        this.senhaHash = senhaHash;
        this.status = StatusPessoa.ATIVO;
        this.papeis.add(papel);
    }

    public Set<Papel> getPapeis() {
        return papeis.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(papeis));
    }

    public boolean possui(Papel papel) {
        return papeis.contains(papel);
    }

    public void adicionarPapel(Papel papel) {
        papeis.add(papel);
    }

    public void removerPapel(Papel papel) {
        papeis.remove(papel);
    }

    public boolean isAtivo() {
        return status == StatusPessoa.ATIVO;
    }

    public void registrarAcesso() {
        this.ultimoAcesso = Instant.now();
    }

    public void alterarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }

    public boolean isEmailConfirmado() {
        return emailConfirmadoEm != null;
    }

    /** Idempotente: guarda a primeira confirmação. */
    public void confirmarEmail() {
        if (emailConfirmadoEm == null) this.emailConfirmadoEm = Instant.now();
    }

    public void atualizarContato(String nome, String telefone) {
        this.nome = nome;
        this.telefone = telefone;
    }

    /** Só completa um CPF ausente: um CPF já informado não muda. */
    public void informarCpf(String cpf) {
        if (this.cpf == null) this.cpf = cpf;
    }
}
