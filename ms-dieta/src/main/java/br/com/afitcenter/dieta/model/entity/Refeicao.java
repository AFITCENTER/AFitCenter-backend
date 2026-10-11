package br.com.afitcenter.dieta.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Refeição da dieta ("Café da manhã"), nos dias da semana ISO (1 = segunda ... 7 = domingo). */
@Getter
@Entity
@Table(name = "refeicao")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refeicao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dieta_id", nullable = false, updatable = false)
    private UUID dietaId;

    @Column(nullable = false)
    private String nome;

    private LocalTime horario;

    @Column(nullable = false)
    private Integer ordem;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dias_semana", nullable = false)
    private Short[] diasSemana;

    private String observacao;

    public Refeicao(UUID dietaId) {
        this.dietaId = dietaId;
    }

    public void preencher(String nome, LocalTime horario, int ordem, Collection<Integer> diasSemana, String observacao) {
        this.nome = nome;
        this.horario = horario;
        this.ordem = ordem;
        this.diasSemana = diasSemana.stream().sorted().distinct().map(Integer::shortValue).toArray(Short[]::new);
        this.observacao = observacao;
    }

    /** Dias em ordem crescente. */
    public List<Integer> getDiasSemana() {
        return Arrays.stream(diasSemana).map(Short::intValue).toList();
    }

    public boolean servidaNoDia(int diaSemana) {
        return Arrays.stream(diasSemana).anyMatch(d -> d == diaSemana);
    }

    public Refeicao copiarPara(UUID dietaId) {
        Refeicao copia = new Refeicao(dietaId);
        copia.preencher(nome, horario, ordem, getDiasSemana(), observacao);
        return copia;
    }
}
