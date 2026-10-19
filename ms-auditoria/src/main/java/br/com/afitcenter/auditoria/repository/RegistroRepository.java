package br.com.afitcenter.auditoria.repository;

import br.com.afitcenter.auditoria.model.dto.Dtos.FiltroRegistros;
import br.com.afitcenter.auditoria.model.entity.RegistroAuditoria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Leitura da trilha. A gravação e a retenção ficam no {@link GravacaoRegistros} (SQL direto). */
public interface RegistroRepository extends JpaRepository<RegistroAuditoria, UUID>,
        JpaSpecificationExecutor<RegistroAuditoria> {

    final class Filtros {

        private Filtros() {
        }

        public static Specification<RegistroAuditoria> de(FiltroRegistros f) {
            return (root, query, cb) -> {
                List<Predicate> condicoes = new ArrayList<>();
                if (f.inicio() != null) condicoes.add(cb.greaterThanOrEqualTo(root.get("ocorridoEm"), f.inicio()));
                if (f.fim() != null) condicoes.add(cb.lessThan(root.get("ocorridoEm"), f.fim()));
                if (f.nutricionistaId() != null) condicoes.add(cb.equal(root.get("nutricionistaId"), f.nutricionistaId()));
                if (f.pacienteId() != null) condicoes.add(cb.equal(root.get("pacienteId"), f.pacienteId()));
                if (f.usuarioId() != null) condicoes.add(cb.equal(root.get("usuarioId"), f.usuarioId()));
                if (f.servico() != null) condicoes.add(cb.equal(root.get("servico"), f.servico()));
                if (f.recurso() != null) condicoes.add(cb.equal(root.get("recurso"), f.recurso()));
                if (f.recursoId() != null) condicoes.add(cb.equal(root.get("recursoId"), f.recursoId()));
                if (f.acao() != null) condicoes.add(cb.equal(root.get("acao"), f.acao()));
                if (f.ip() != null) condicoes.add(cb.equal(root.get("ip"), f.ip()));
                return cb.and(condicoes.toArray(Predicate[]::new));
            };
        }
    }
}
