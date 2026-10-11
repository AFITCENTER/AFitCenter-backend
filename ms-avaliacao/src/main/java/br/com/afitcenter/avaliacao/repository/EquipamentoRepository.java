package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.Equipamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Visível para o nutricionista = catálogo do sistema ({@code nutricionista_id} nulo) + os próprios. */
public interface EquipamentoRepository extends JpaRepository<Equipamento, Integer> {

    @Query("""
            SELECT e FROM Equipamento e
            WHERE e.nutricionistaId IS NULL OR e.nutricionistaId = :nutricionistaId
            ORDER BY e.marca, e.modelo, e.id
            """)
    List<Equipamento> findVisiveis(@Param("nutricionistaId") UUID nutricionistaId);

    /** Mesma marca e modelo (sem diferenciar maiúsculas) entre os visíveis, fora {@code ignorarId}. */
    @Query("""
            SELECT COUNT(e) > 0 FROM Equipamento e
            WHERE (e.nutricionistaId IS NULL OR e.nutricionistaId = :nutricionistaId)
              AND lower(e.marca) = lower(:marca) AND lower(e.modelo) = lower(:modelo)
              AND e.id <> :ignorarId
            """)
    boolean existeVisivel(@Param("nutricionistaId") UUID nutricionistaId, @Param("marca") String marca,
                          @Param("modelo") String modelo, @Param("ignorarId") Integer ignorarId);
}
