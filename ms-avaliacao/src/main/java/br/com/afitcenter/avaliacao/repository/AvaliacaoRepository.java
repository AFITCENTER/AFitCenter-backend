package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.Avaliacao;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID>, JpaSpecificationExecutor<Avaliacao> {

    Optional<Avaliacao> findByIdAndNutricionistaIdAndDeletadoEmIsNull(UUID id, UUID nutricionistaId);

    /** Inclui avaliações excluídas: a FK continua apontando para o equipamento. */
    boolean existsByEquipamentoId(Integer equipamentoId);

    /** Filtros da listagem. Critérios nulos são ignorados pelo Spring Data. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Avaliacao> doNutricionista(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.equal(root.get("nutricionistaId"), nutricionistaId),
                    cb.isNull(root.get("deletadoEm")));
        }

        public static Specification<Avaliacao> doPaciente(UUID pacienteId) {
            return pacienteId == null ? null : (root, query, cb) -> cb.equal(root.get("pacienteId"), pacienteId);
        }

        public static Specification<Avaliacao> aPartirDe(OffsetDateTime inicio) {
            return inicio == null ? null
                    : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataHoraMedicao"), inicio);
        }

        public static Specification<Avaliacao> antesDe(OffsetDateTime fim) {
            return fim == null ? null : (root, query, cb) -> cb.lessThan(root.get("dataHoraMedicao"), fim);
        }
    }
}
