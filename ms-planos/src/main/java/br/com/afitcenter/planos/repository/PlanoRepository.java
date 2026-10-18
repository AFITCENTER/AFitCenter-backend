package br.com.afitcenter.planos.repository;

import br.com.afitcenter.planos.model.entity.Plano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoRepository extends JpaRepository<Plano, Integer> {

    List<Plano> findAllByAtivoTrueAndPublicoTrueOrderByOrdemAscIdAsc();

    List<Plano> findAllByOrderByOrdemAscIdAsc();

    Optional<Plano> findByPadraoTrue();

    Optional<Plano> findByIdAndAtivoTrue(Integer id);

    boolean existsByCodigo(String codigo);
}
