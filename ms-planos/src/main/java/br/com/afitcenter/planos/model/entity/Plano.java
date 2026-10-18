package br.com.afitcenter.planos.model.entity;

import br.com.afitcenter.core.base.model.EntidadeAuditavel;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Plano da plataforma. Limites nulos = ilimitado. O plano {@code padrao} (gratuito) é o de quem nunca
 * assinou, de quem cancelou e o que vale durante a suspensão por falta de pagamento.
 */
@Getter
@Entity
@Table(name = "plano")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plano extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, updatable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    private String descricao;

    @Column(name = "preco_mensal", nullable = false)
    private BigDecimal precoMensal;

    /** Nulo = o plano não é vendido no ciclo anual. */
    @Column(name = "preco_anual")
    private BigDecimal precoAnual;

    @Column(name = "limite_pacientes")
    private Integer limitePacientes;

    @Column(name = "limite_consultorios")
    private Integer limiteConsultorios;

    @Column(nullable = false, updatable = false)
    private boolean padrao;

    /** Aparece no catálogo público. */
    @Column(nullable = false)
    private boolean publico;

    /** Aceita novas assinaturas. Quem já assina um plano desativado continua renovando. */
    @Column(nullable = false)
    private boolean ativo;

    @Column(nullable = false)
    private int ordem;

    @Version
    @Column(nullable = false)
    private Long versao;

    public Plano(String codigo, String nome, String descricao, BigDecimal precoMensal, BigDecimal precoAnual,
                 Integer limitePacientes, Integer limiteConsultorios, boolean publico, int ordem) {
        this.codigo = codigo;
        this.ativo = true;
        atualizar(nome, descricao, precoMensal, precoAnual, limitePacientes, limiteConsultorios, publico, ordem);
    }

    public void atualizar(String nome, String descricao, BigDecimal precoMensal, BigDecimal precoAnual,
                          Integer limitePacientes, Integer limiteConsultorios, boolean publico, int ordem) {
        this.nome = nome;
        this.descricao = descricao;
        this.precoMensal = precoMensal;
        this.precoAnual = precoAnual;
        this.limitePacientes = limitePacientes;
        this.limiteConsultorios = limiteConsultorios;
        this.publico = publico;
        this.ordem = ordem;
    }

    public void alterarAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean gratuito() {
        return precoMensal.signum() == 0;
    }

    public Optional<BigDecimal> preco(Ciclo ciclo) {
        return Optional.ofNullable(ciclo == Ciclo.ANUAL ? precoAnual : precoMensal);
    }

    public boolean mesmosLimites(Integer pacientes, Integer consultorios) {
        return Objects.equals(limitePacientes, pacientes) && Objects.equals(limiteConsultorios, consultorios);
    }
}
