package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.Alimento;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A base oficial é compartilhada; alimentos próprios só são visíveis ao dono. Use os métodos que
 * recebem {@code nutricionistaId}: os herdados ({@code findById}...) não isolam o tenant.
 */
public interface AlimentoRepository extends JpaRepository<Alimento, Integer>, JpaSpecificationExecutor<Alimento> {

    /** Ativo e da base oficial ou do próprio nutricionista. */
    @Query("""
            SELECT a FROM Alimento a
            WHERE a.id = :id AND a.ativo = true AND (a.nutricionistaId IS NULL OR a.nutricionistaId = :nutricionistaId)
            """)
    Optional<Alimento> buscarVisivel(@Param("id") Integer id, @Param("nutricionistaId") UUID nutricionistaId);

    boolean existsByNutricionistaIdAndAtivoTrueAndNomeIgnoreCaseAndIdNot(UUID nutricionistaId, String nome, Integer ignorarId);

    final class Filtros {

        private Filtros() {
        }

        /** Base oficial (sem dono) + alimentos do próprio nutricionista, só os ativos. */
        public static Specification<Alimento> visiveisPara(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.isTrue(root.get("ativo")),
                    cb.or(cb.isNull(root.get("nutricionistaId")), cb.equal(root.get("nutricionistaId"), nutricionistaId)));
        }

        public static Specification<Alimento> comNome(String busca) {
            if (busca == null || busca.isBlank()) return null;
            String padrao = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, query, cb) -> cb.like(cb.lower(root.get("nome")), padrao);
        }

        public static Specification<Alimento> doGrupo(Integer grupoId) {
            return grupoId == null ? null : (root, query, cb) -> cb.equal(root.get("grupo").get("id"), grupoId);
        }

        /** {@code true}: só os próprios; {@code false}: só a base oficial. */
        public static Specification<Alimento> proprios(Boolean proprios) {
            if (proprios == null) return null;
            return proprios
                    ? (root, query, cb) -> cb.isNotNull(root.get("nutricionistaId"))
                    : (root, query, cb) -> cb.isNull(root.get("nutricionistaId"));
        }
    }
}
