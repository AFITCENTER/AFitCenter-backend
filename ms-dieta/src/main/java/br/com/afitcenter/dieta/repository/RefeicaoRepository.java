package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.Refeicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Refeições não têm tenant próprio: valide antes a dieta com o nutricionista. */
public interface RefeicaoRepository extends JpaRepository<Refeicao, UUID> {

    List<Refeicao> findAllByDietaIdOrderByOrdemAscHorarioAsc(UUID dietaId);

    Optional<Refeicao> findByIdAndDietaId(UUID id, UUID dietaId);

    @Query("SELECT COALESCE(MAX(r.ordem), 0) FROM Refeicao r WHERE r.dietaId = :dietaId")
    int maiorOrdem(@Param("dietaId") UUID dietaId);
}
