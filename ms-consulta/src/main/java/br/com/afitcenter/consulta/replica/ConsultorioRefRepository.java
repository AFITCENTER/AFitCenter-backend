package br.com.afitcenter.consulta.replica;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConsultorioRefRepository {

    private static final RowMapper<ConsultorioRef> MAPPER = (rs, i) -> new ConsultorioRef(
            rs.getObject("id", UUID.class), rs.getObject("nutricionista_id", UUID.class), rs.getString("nome"),
            rs.getBoolean("ativo"), rs.getLong("versao"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ConsultorioRefRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<ConsultorioRef> buscar(UUID id) {
        return jdbc.query("SELECT * FROM consultorio_ref WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<ConsultorioRef> buscarTodos(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return named.query("SELECT * FROM consultorio_ref WHERE id IN (:ids)", Map.of("ids", ids), MAPPER);
    }

    /** Upsert só com versão mais nova: reentregas e eventos fora de ordem são ignorados. */
    public boolean aplicar(ConsultorioRef ref) {
        return jdbc.update("""
                INSERT INTO consultorio_ref (id, nutricionista_id, nome, ativo, versao)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    nome = EXCLUDED.nome,
                    ativo = EXCLUDED.ativo,
                    versao = EXCLUDED.versao,
                    sincronizado_em = now()
                WHERE consultorio_ref.versao < EXCLUDED.versao
                """, ref.id(), ref.nutricionistaId(), ref.nome(), ref.ativo(), ref.versao()) > 0;
    }
}
