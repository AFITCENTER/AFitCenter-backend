package br.com.afitcenter.consulta.repository;

import br.com.afitcenter.consulta.model.entity.Consulta;
import br.com.afitcenter.consulta.model.enums.Enums.StatusConsulta;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface ConsultaRepository extends JpaRepository<Consulta, UUID>, JpaSpecificationExecutor<Consulta> {

    Optional<Consulta> findByIdAndNutricionistaIdAndDeletadoEmIsNull(UUID id, UUID nutricionistaId);

    Optional<Consulta> findByIdAndDeletadoEmIsNull(UUID id);

    /**
     * Existe outra consulta do nutricionista ocupando parte do intervalo [inicio, fim)?
     * {@code ignorarId} exclui a própria consulta na edição (passe um UUID qualquer na criação).
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM consulta c
                WHERE c.nutricionista_id = :nutricionistaId
                  AND c.deletado_em IS NULL
                  AND c.status NOT IN ('CANCELADA', 'FALTOU')
                  AND c.id <> :ignorarId
                  AND c.data_hora < :fim
                  AND c.data_hora + make_interval(mins => c.duracao_min) > :inicio)
            """, nativeQuery = true)
    boolean existeConflito(@Param("nutricionistaId") UUID nutricionistaId, @Param("ignorarId") UUID ignorarId,
                           @Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    /**
     * Bloqueia a agenda do nutricionista até o fim da transação (advisory lock do PostgreSQL). Marcações simultâneas
     * do mesmo nutricionista esperam uma pela outra, e a verificação de conflito de quem esperou já enxerga o que a
     * outra gravou. Chame antes de {@link #existeConflito}, dentro da transação que grava a consulta.
     */
    default void bloquearAgenda(UUID nutricionistaId) {
        bloquearChave(nutricionistaId.getMostSignificantBits() ^ nutricionistaId.getLeastSignificantBits());
    }

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:chave)", nativeQuery = true)
    int bloquearChave(@Param("chave") long chave);

    /** Filtros da agenda. Critérios nulos são ignorados pelo Spring Data. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Consulta> doNutricionista(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.equal(root.get("nutricionistaId"), nutricionistaId),
                    cb.isNull(root.get("deletadoEm")));
        }

        public static Specification<Consulta> aPartirDe(OffsetDateTime inicio) {
            return inicio == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataHora"), inicio);
        }

        public static Specification<Consulta> antesDe(OffsetDateTime fim) {
            return fim == null ? null : (root, query, cb) -> cb.lessThan(root.get("dataHora"), fim);
        }

        public static Specification<Consulta> doPaciente(UUID pacienteId) {
            return pacienteId == null ? null : (root, query, cb) -> cb.equal(root.get("pacienteId"), pacienteId);
        }

        public static Specification<Consulta> comStatus(StatusConsulta status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }
    }
}
