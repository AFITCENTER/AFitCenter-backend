package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.RefeicaoItemSubstituto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Substitutos não têm tenant próprio: valide antes a dieta, a refeição e o item. */
public interface RefeicaoItemSubstitutoRepository extends JpaRepository<RefeicaoItemSubstituto, UUID> {

    List<RefeicaoItemSubstituto> findAllByRefeicaoItemIdIn(Collection<UUID> refeicaoItemIds);

    Optional<RefeicaoItemSubstituto> findByIdAndRefeicaoItemId(UUID id, UUID refeicaoItemId);
}
