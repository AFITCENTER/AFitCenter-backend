package br.com.afitcenter.core.assinatura;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso à tabela {@code assinatura_ref}. Tabela exigida na migração do serviço consumidor:
 *
 * <pre>
 * CREATE TABLE assinatura_ref (
 *   nutricionista_id     uuid PRIMARY KEY,
 *   assinatura_id        uuid NOT NULL,
 *   plano_codigo         text NOT NULL,
 *   status               text NOT NULL,
 *   limite_pacientes     integer,          -- NULL = ilimitado
 *   limite_consultorios  integer,
 *   versao               bigint NOT NULL,
 *   sincronizado_em      timestamptz NOT NULL DEFAULT now()
 * );
 * </pre>
 */
public class AssinaturaRefRepository {

    private static final RowMapper<AssinaturaRef> MAPPER = (rs, i) -> new AssinaturaRef(
            rs.getObject("nutricionista_id", UUID.class),
            rs.getObject("assinatura_id", UUID.class),
            rs.getString("plano_codigo"),
            rs.getString("status"),
            rs.getObject("limite_pacientes", Integer.class),
            rs.getObject("limite_consultorios", Integer.class),
            rs.getLong("versao"));

    private final JdbcTemplate jdbc;

    public AssinaturaRefRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AssinaturaRef> buscar(UUID nutricionistaId) {
        return jdbc.query("SELECT * FROM assinatura_ref WHERE nutricionista_id = ?", MAPPER, nutricionistaId)
                .stream().findFirst();
    }

    /**
     * Mesma leitura com {@code FOR UPDATE}: dentro da transação que cria o recurso, duas criações simultâneas
     * do mesmo nutricionista esperam uma pela outra e não passam juntas do limite.
     */
    public Optional<AssinaturaRef> bloquear(UUID nutricionistaId) {
        return jdbc.query("SELECT * FROM assinatura_ref WHERE nutricionista_id = ? FOR UPDATE", MAPPER, nutricionistaId)
                .stream().findFirst();
    }

    /**
     * Insere ou atualiza a réplica, só se a versão recebida for mais nova.
     *
     * @return {@code true} se a réplica mudou
     */
    public boolean aplicar(AssinaturaRef ref) {
        return jdbc.update("""
                INSERT INTO assinatura_ref (nutricionista_id, assinatura_id, plano_codigo, status, limite_pacientes,
                                            limite_consultorios, versao)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (nutricionista_id) DO UPDATE SET
                    assinatura_id = EXCLUDED.assinatura_id,
                    plano_codigo = EXCLUDED.plano_codigo,
                    status = EXCLUDED.status,
                    limite_pacientes = EXCLUDED.limite_pacientes,
                    limite_consultorios = EXCLUDED.limite_consultorios,
                    versao = EXCLUDED.versao,
                    sincronizado_em = now()
                WHERE assinatura_ref.versao < EXCLUDED.versao
                """, ref.nutricionistaId(), ref.assinaturaId(), ref.planoCodigo(), ref.status(),
                ref.limitePacientes(), ref.limiteConsultorios(), ref.versao()) > 0;
    }
}
