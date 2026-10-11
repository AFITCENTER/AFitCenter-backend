package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.Exame;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface ExameRepository extends JpaRepository<Exame, UUID>, JpaSpecificationExecutor<Exame> {

    Optional<Exame> findByIdAndNutricionistaIdAndDeletadoEmIsNull(UUID id, UUID nutricionistaId);

    /** Filtros da listagem. Critérios nulos são ignorados pelo Spring Data. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Exame> doNutricionista(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.equal(root.get("nutricionistaId"), nutricionistaId),
                    cb.isNull(root.get("deletadoEm")));
        }

        public static Specification<Exame> doPaciente(UUID pacienteId) {
            return pacienteId == null ? null : (root, query, cb) -> cb.equal(root.get("pacienteId"), pacienteId);
        }

        public static Specification<Exame> coletadoDesde(LocalDate inicio) {
            return inicio == null ? null
                    : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataColeta"), inicio);
        }

        public static Specification<Exame> coletadoAte(LocalDate fim) {
            return fim == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataColeta"), fim);
        }
    }
}
