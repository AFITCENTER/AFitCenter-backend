package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.RefeicaoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Itens não têm tenant próprio: valide antes a dieta e a refeição. */
public interface RefeicaoItemRepository extends JpaRepository<RefeicaoItem, UUID> {

    List<RefeicaoItem> findAllByRefeicaoIdInOrderByOpcaoAscOrdemAsc(Collection<UUID> refeicaoIds);

    Optional<RefeicaoItem> findByIdAndRefeicaoId(UUID id, UUID refeicaoId);

    @Query("SELECT COALESCE(MAX(i.ordem), 0) FROM RefeicaoItem i WHERE i.refeicaoId = :refeicaoId")
    int maiorOrdem(@Param("refeicaoId") UUID refeicaoId);
}
