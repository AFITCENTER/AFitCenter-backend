package br.com.afitcenter.avaliacao.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Leitura da view {@code vw_evolucao}: resultados das avaliações, peso/altura/IMC e exames numa série
 * única. Toda consulta filtra por nutricionista e paciente. A ordem é a do catálogo (métrica, depois
 * região, corpo inteiro primeiro) e, dentro de cada série, a cronológica.
 */
@Repository
public class EvolucaoRepository {

    private static final String SELECT = """
            SELECT v.origem, v.origem_id, v.data_hora, v.equipamento_id, v.metrica, v.metrica_nome, v.unidade,
                   v.regiao, v.valor, v.ref_min, v.ref_max
            FROM vw_evolucao v
            JOIN metrica m ON m.codigo = v.metrica
            LEFT JOIN regiao_corporal rc ON rc.codigo = v.regiao
            WHERE v.nutricionista_id = :nutricionistaId AND v.paciente_id = :pacienteId
            """;
    private static final String ORDEM = " ORDER BY m.id, rc.id NULLS FIRST, v.data_hora, v.origem_id";

    private static final RowMapper<Linha> MAPPER = (rs, i) -> new Linha(
            rs.getString("origem"), rs.getObject("origem_id", UUID.class),
            rs.getObject("data_hora", OffsetDateTime.class), rs.getObject("equipamento_id", Integer.class),
            rs.getString("metrica"), rs.getString("metrica_nome"), rs.getString("unidade"), rs.getString("regiao"),
            rs.getBigDecimal("valor"), rs.getBigDecimal("ref_min"), rs.getBigDecimal("ref_max"));

    private final NamedParameterJdbcTemplate named;

    public EvolucaoRepository(JdbcTemplate jdbc) {
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * Pontos das métricas pedidas. Sem {@code regiao}, traz todas as regiões (e o corpo inteiro);
     * período {@code [inicio, fim)}, com limites opcionais.
     */
    public List<Linha> serie(UUID nutricionistaId, UUID pacienteId, Collection<String> metricas, String regiao,
                             OffsetDateTime inicio, OffsetDateTime fim) {
        var sql = new StringBuilder(SELECT).append(" AND v.metrica IN (:metricas)");
        var params = parametros(nutricionistaId, pacienteId).addValue("metricas", metricas);
        if (regiao != null) {
            sql.append(" AND v.regiao = :regiao");
            params.addValue("regiao", regiao);
        }
        if (inicio != null) {
            sql.append(" AND v.data_hora >= :inicio");
            params.addValue("inicio", inicio);
        }
        if (fim != null) {
            sql.append(" AND v.data_hora < :fim");
            params.addValue("fim", fim);
        }
        return named.query(sql.append(ORDEM).toString(), params, MAPPER);
    }

    /** Todos os valores (inclusive peso, altura e IMC) das avaliações informadas. */
    public List<Linha> daAvaliacoes(UUID nutricionistaId, UUID pacienteId, Collection<UUID> avaliacoes) {
        String sql = SELECT + " AND v.origem = 'AVALIACAO' AND v.origem_id IN (:avaliacoes)" + ORDEM;
        return named.query(sql, parametros(nutricionistaId, pacienteId).addValue("avaliacoes", avaliacoes), MAPPER);
    }

    private static MapSqlParameterSource parametros(UUID nutricionistaId, UUID pacienteId) {
        return new MapSqlParameterSource()
                .addValue("nutricionistaId", nutricionistaId)
                .addValue("pacienteId", pacienteId);
    }

    /** Uma linha da view. {@code origem} é AVALIACAO ou EXAME; {@code regiao} nula = corpo inteiro. */
    public record Linha(String origem, UUID origemId, OffsetDateTime dataHora, Integer equipamentoId, String metrica,
                        String metricaNome, String unidade, String regiao, BigDecimal valor, BigDecimal refMin,
                        BigDecimal refMax) {
    }
}
