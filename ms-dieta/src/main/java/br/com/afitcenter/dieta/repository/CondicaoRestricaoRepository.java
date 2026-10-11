package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.CondicaoRestricao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CondicaoRestricaoRepository extends JpaRepository<CondicaoRestricao, Integer> {

    List<CondicaoRestricao> findAllByCondicaoIdIn(Collection<Integer> condicaoIds);
}
