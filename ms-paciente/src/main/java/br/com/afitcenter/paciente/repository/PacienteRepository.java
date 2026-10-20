package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.Paciente;
import br.com.afitcenter.paciente.model.enums.Enums.StatusPaciente;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface PacienteRepository extends JpaRepository<Paciente, UUID>, JpaSpecificationExecutor<Paciente> {

    Optional<Paciente> findByIdAndNutricionistaIdAndDeletadoEmIsNull(UUID id, UUID nutricionistaId);

    boolean existsByNutricionistaIdAndCpfAndDeletadoEmIsNull(UUID nutricionistaId, String cpf);

    /** Fichas que contam no limite do plano (as excluídas não contam). */
    long countByNutricionistaIdAndDeletadoEmIsNull(UUID nutricionistaId);

    boolean existsByNutricionistaIdAndCpfAndDeletadoEmIsNullAndIdNot(UUID nutricionistaId, String cpf, UUID id);

    // ---------------------------------------------------------------- portal do paciente (por conta, não por tenant)

    Optional<Paciente> findByIdAndDeletadoEmIsNull(UUID id);

    /** Fichas ativas ligadas à conta, de todos os nutricionistas. */
    List<Paciente> findAllByPessoaIdAndDeletadoEmIsNullOrderByPessoaVinculadaEm(UUID pessoaId);

    boolean existsByNutricionistaIdAndPessoaIdAndDeletadoEmIsNull(UUID nutricionistaId, UUID pessoaId);

    /** Filtros da listagem. Critérios nulos são ignorados pelo Spring Data. */
    final class Filtros {

        private Filtros() {
        }

        public static Specification<Paciente> doNutricionista(UUID nutricionistaId) {
            return (root, query, cb) -> cb.and(
                    cb.equal(root.get("nutricionistaId"), nutricionistaId),
                    cb.isNull(root.get("deletadoEm")));
        }

        public static Specification<Paciente> comStatus(StatusPaciente status) {
            return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        /** Parte do nome ou nome social, ou o CPF completo (com ou sem pontuação). */
        public static Specification<Paciente> comBusca(String busca) {
            if (busca == null || busca.isBlank()) return null;
            String padrao = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
            String digitos = busca.replaceAll("\\D", "");
            return (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nome")), padrao),
                    cb.like(cb.lower(cb.coalesce(root.get("nomeSocial"), "")), padrao),
                    cb.equal(root.get("cpf"), digitos.length() == 11 ? digitos : "-"));
        }
    }
}
