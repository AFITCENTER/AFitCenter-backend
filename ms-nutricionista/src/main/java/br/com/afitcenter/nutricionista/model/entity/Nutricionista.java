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
 * Perfil profissional. O id é o mesmo da pessoa no ms-pessoa (dona do nome, CPF, e-mail e telefone) e funciona
 * como tenant: todo dado clínico guarda {@code nutricionista_id} igual a este id.
 */
@Getter
@Entity
@Table(name = "nutricionista")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nutricionista extends EntidadeAuditavel implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "crn_regiao", nullable = false)
    private Integer crnRegiao;

    @Column(name = "crn_numero", nullable = false, length = 20)
    private String crnNumero;

    private String especialidades;

    @Column(name = "assinatura_url")
    private String assinaturaUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    /** Endereço profissional (o do PDF da dieta), não o residencial. */
    @Embedded
    private Endereco endereco;

    /** Id atribuído (não gerado): sem isto o Spring Data faria um SELECT antes do INSERT. */
    @Transient
    private boolean novo;

    public Nutricionista(UUID id, Integer crnRegiao, String crnNumero) {
        this.id = id;
        this.crnRegiao = crnRegiao;
        this.crnNumero = crnNumero;
        this.novo = true;
    }

    public void atualizarPerfil(String especialidades, String assinaturaUrl, String logoUrl, Endereco endereco) {
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
