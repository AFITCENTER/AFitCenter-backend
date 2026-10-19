package br.com.afitcenter.pagamentos.repository;

import br.com.afitcenter.pagamentos.model.entity.Cobranca;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nas rotas do nutricionista, filtre sempre por {@code nutricionistaId}. */
public interface CobrancaRepository extends JpaRepository<Cobranca, UUID>, JpaSpecificationExecutor<Cobranca> {

    Optional<Cobranca> findByChaveIdempotencia(String chaveIdempotencia);

    Optional<Cobranca> findByIdAndNutricionistaId(UUID id, UUID nutricionistaId);

    Optional<Cobranca> findByProvedorAndIdExterno(String provedor, String idExterno);

    List<Cobranca> findAllByOrigemAndReferenciaIdAndStatus(String origem, UUID referenciaId, StatusCobranca status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cobranca c WHERE c.id = :id")
    Optional<Cobranca> bloquear(@Param("id") UUID id);

    /** Pendentes com vencimento anterior a {@code limite}. */
    @Query(value = """
            SELECT id FROM cobranca WHERE status = 'PENDENTE' AND vencimento < :limite ORDER BY vencimento LIMIT :lote
            """, nativeQuery = true)
    List<UUID> vencidasAntesDe(@Param("limite") LocalDate limite, @Param("lote") int lote);

    final class Filtros {

        private Filtros() {
        }

        public static Specification<Cobranca> doNutricionista(UUID nutricionistaId) {
            return nutricionistaId == null ? null
                    : (root, query, cb) -> cb.equal(root.get("nutricionistaId"), nutricionistaId);
        }

        public static Specification<Cobranca> comStatus(StatusCobranca status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }
    }
}
