package br.com.afitcenter.core.paciente;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à tabela {@code paciente_ref}. Tabela exigida na migração do serviço consumidor:
 *
 * <pre>
 * CREATE TABLE paciente_ref (
 *   id               uuid PRIMARY KEY,
 *   nutricionista_id uuid NOT NULL,
 *   nome             text NOT NULL,
 *   sexo             varchar(1) NOT NULL,
 *   data_nascimento  date NOT NULL,
 *   status           text NOT NULL,
 *   excluido         boolean NOT NULL DEFAULT false,
 *   versao           bigint NOT NULL,
 *   sincronizado_em  timestamptz NOT NULL DEFAULT now(),
 *   UNIQUE (id, nutricionista_id)   -- alvo das FKs compostas (paciente_id, nutricionista_id)
 * );
 * </pre>
 */
public class PacienteRefRepository {

    private static final RowMapper<PacienteRef> MAPPER = (rs, i) -> new PacienteRef(
            rs.getObject("id", UUID.class),
            rs.getObject("nutricionista_id", UUID.class),
            rs.getString("nome"),
            rs.getString("sexo"),
            rs.getObject("data_nascimento", java.time.LocalDate.class),
            rs.getString("status"),
            rs.getBoolean("excluido"),
            rs.getLong("versao"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PacienteRefRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public Optional<PacienteRef> buscar(UUID id) {
        return jdbc.query("SELECT * FROM paciente_ref WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<PacienteRef> buscarTodos(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return named.query("SELECT * FROM paciente_ref WHERE id IN (:ids)", Map.of("ids", ids), MAPPER);
    }

    /**
     * Insere ou atualiza a réplica, só se a versão recebida for mais nova. Reentregas e eventos
     * fora de ordem são ignorados sem erro.
     *
     * @return {@code true} se a réplica mudou
     */
    public boolean aplicar(PacienteRef ref) {
        return jdbc.update("""
                INSERT INTO paciente_ref (id, nutricionista_id, nome, sexo, data_nascimento, status, excluido, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    nome = EXCLUDED.nome,
                    sexo = EXCLUDED.sexo,
                    data_nascimento = EXCLUDED.data_nascimento,
                    status = EXCLUDED.status,
                    excluido = EXCLUDED.excluido,
                    versao = EXCLUDED.versao,
                    sincronizado_em = now()
                WHERE paciente_ref.versao < EXCLUDED.versao
                """, ref.id(), ref.nutricionistaId(), ref.nome(), ref.sexo(), ref.dataNascimento(),
                ref.status(), ref.excluido(), ref.versao()) > 0;
    }
}
