package br.com.afitcenter.nutricionista.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Perfil profissional. O id é o mesmo do usuário no ms-autenticacao e funciona como tenant:
 * todo dado clínico guarda {@code nutricionista_id} igual a este id.
 */
@Getter
@Entity
@Table(name = "nutricionista")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nutricionista extends EntidadeAuditavel implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 11, updatable = false)
    private String cpf;

    @Column(name = "crn_regiao", nullable = false)
    private Integer crnRegiao;

    @Column(name = "crn_numero", nullable = false, length = 20)
    private String crnNumero;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String telefone;

    private String especialidades;

    @Column(name = "assinatura_url")
    private String assinaturaUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    @Embedded
    private Endereco endereco;

    /** Id atribuído (não gerado): sem isto o Spring Data faria um SELECT antes do INSERT. */
    @Transient
    private boolean novo;

    public Nutricionista(UUID id, String nome, String cpf, Integer crnRegiao, String crnNumero, String email,
                         String telefone) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.crnRegiao = crnRegiao;
        this.crnNumero = crnNumero;
        this.email = email;
        this.telefone = telefone;
        this.novo = true;
    }

    public void atualizarPerfil(String nome, String telefone, String especialidades, String assinaturaUrl,
                                String logoUrl, Endereco endereco) {
        this.nome = nome;
        this.telefone = telefone;
        this.especialidades = especialidades;
        this.assinaturaUrl = assinaturaUrl;
        this.logoUrl = logoUrl;
        this.endereco = endereco;
    }

    public String crnFormatado() {
        return "CRN-" + crnRegiao + " " + crnNumero;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostLoad
    @PostPersist
    void marcarPersistido() {
        this.novo = false;
    }
}
