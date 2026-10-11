package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.Metrica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MetricaRepository extends JpaRepository<Metrica, Integer> {

    List<Metrica> findAllByOrderByCategoriaAscIdAsc();

    List<Metrica> findAllByCategoriaOrderByIdAsc(String categoria);

    List<Metrica> findAllByCodigoIn(Collection<String> codigos);
}
