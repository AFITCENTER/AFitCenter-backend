package br.com.afitcenter.avaliacao.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Item do catálogo de métricas: tudo o que se mede numa avaliação ou exame (GORDURA_PCT,
 * IMPEDANCIA_20KHZ, PERIMETRO_CM, GLICEMIA_JEJUM...). Cada valor medido vira uma linha em
 * {@code avaliacao_resultado} ou {@code exame_resultado}, o que permite aparelhos e protocolos
 * diferentes sem mudar o esquema.
 */
@Getter
@Entity
@Table(name = "metrica")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Metrica {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String unidade;

    @Column(nullable = false)
    private String categoria;

    @Column(name = "casas_decimais", nullable = false)
    private Integer casasDecimais;

    /** PESO, ALTURA e IMC: ficam em colunas da avaliação, nunca em {@code avaliacao_resultado}. */
    public boolean isBasica() {
        return "BASICA".equals(categoria);
    }

    /** Só entra em exames. */
    public boolean isLaboratorial() {
        return "LABORATORIAL".equals(categoria);
    }

    /**
     * Tipo de região corporal que a métrica exige ({@code SEGMENTO}, {@code PERIMETRO} ou {@code DOBRA}),
     * ou {@code null} se o valor é do corpo inteiro e não aceita região.
     */
    public String tipoRegiaoExigido() {
        return switch (categoria) {
            case "SEGMENTAR", "IMPEDANCIA" -> "SEGMENTO";
            case "PERIMETRO" -> "PERIMETRO";
            case "DOBRA_CUTANEA" -> "DOBRA";
            default -> null;
        };
    }
}
