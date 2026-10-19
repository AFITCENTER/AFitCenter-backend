package br.com.afitcenter.auditoria.repository;

import br.com.afitcenter.core.mensageria.evento.AuditoriaEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/** Escrita na trilha, em SQL direto: inserção idempotente e a única exclusão permitida (retenção). */
@Repository
public class GravacaoRegistros {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GravacaoRegistros(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** @return {@code false} se o evento já estava gravado (reentrega) */
    public boolean inserir(AuditoriaEvento e) {
        return jdbc.update("""
                INSERT INTO registro_auditoria (id, ocorrido_em, servico, acao, recurso, recurso_id, paciente_id,
                    nutricionista_id, usuario_id, papel, metodo, rota, status, ip, detalhes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO NOTHING
                """, e.eventoId(), Timestamp.from(e.ocorridoEm()), e.servico(), e.acao(), e.recurso(), e.recursoId(),
                e.pacienteId(), e.nutricionistaId(), e.usuarioId(), e.papel(), e.metodo(), e.rota(), e.status(),
                e.ip(), json(e)) > 0;
    }

    /**
     * Apaga registros anteriores a {@code limite}, em lotes. O gatilho da tabela só permite a exclusão com
     * {@code afitcenter.retencao} ligado na transação.
     */
    @Transactional
    public int apagarAnterioresA(Instant limite, int lote) {
        jdbc.execute("SET LOCAL afitcenter.retencao = 'on'");
        return jdbc.update("""
                DELETE FROM registro_auditoria
                WHERE id IN (SELECT id FROM registro_auditoria WHERE ocorrido_em < ? ORDER BY ocorrido_em LIMIT ?)
                """, Timestamp.from(limite), lote);
    }

    private String json(AuditoriaEvento e) {
        try {
            return objectMapper.writeValueAsString(e.detalhes());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Detalhes do registro de auditoria ilegíveis.", ex);
        }
    }
}
