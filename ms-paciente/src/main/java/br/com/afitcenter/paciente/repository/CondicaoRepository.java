package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.Condicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CondicaoRepository extends JpaRepository<Condicao, Integer> {

    /** Catálogo do sistema + condições do próprio nutricionista. */
    @Query("""
            SELECT c FROM Condicao c
            WHERE (c.nutricionistaId IS NULL OR c.nutricionistaId = :nutricionistaId)
              AND (:tipoId IS NULL OR c.tipo.id = :tipoId)
            ORDER BY c.tipo.nome, c.nome""")
    List<Condicao> findVisiveis(@Param("nutricionistaId") UUID nutricionistaId, @Param("tipoId") Integer tipoId);

    @Query("""
            SELECT c FROM Condicao c
            WHERE c.id = :id AND (c.nutricionistaId IS NULL OR c.nutricionistaId = :nutricionistaId)""")
    Optional<Condicao> findVisivel(@Param("id") Integer id, @Param("nutricionistaId") UUID nutricionistaId);

    Optional<Condicao> findByIdAndNutricionistaId(Integer id, UUID nutricionistaId);
}
