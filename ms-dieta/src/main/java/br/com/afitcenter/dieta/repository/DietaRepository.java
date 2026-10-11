package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.Dieta;
import br.com.afitcenter.dieta.model.enums.Enums.StatusDieta;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface DietaRepository extends JpaRepository<Dieta, UUID>, JpaSpecificationExecutor<Dieta> {

    Optional<Dieta> findByIdAndNutricionistaIdAndDeletadoEmIsNull(UUID id, UUID nutricionistaId);

    boolean existsByPacienteIdAndStatusAndDeletadoEmIsNullAndIdNot(UUID pacienteId, StatusDieta status, UUID ignorarId);

    /** Filtros das listagens. Critérios nulos são ignorados pelo Spring Data. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Dieta> doNutricionista(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.equal(root.get("nutricionistaId"), nutricionistaId),
                    cb.isNull(root.get("deletadoEm")));
        }

        public static Specification<Dieta> modelos(boolean modelos) {
            return (root, query, cb) -> cb.equal(root.get("modelo"), modelos);
        }

        public static Specification<Dieta> doPaciente(UUID pacienteId) {
            return pacienteId == null ? null : (root, query, cb) -> cb.equal(root.get("pacienteId"), pacienteId);
        }

        public static Specification<Dieta> comStatus(StatusDieta status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        public static Specification<Dieta> comTitulo(String busca) {
            if (busca == null || busca.isBlank()) return null;
            String padrao = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, query, cb) -> cb.like(cb.lower(root.get("titulo")), padrao);
        }
    }
}
