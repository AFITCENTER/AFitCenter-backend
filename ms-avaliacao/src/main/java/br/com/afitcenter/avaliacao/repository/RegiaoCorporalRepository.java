package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.RegiaoCorporal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RegiaoCorporalRepository extends JpaRepository<RegiaoCorporal, Integer> {

    List<RegiaoCorporal> findAllByOrderByTipoAscIdAsc();

    List<RegiaoCorporal> findAllByTipoOrderByIdAsc(String tipo);

    List<RegiaoCorporal> findAllByCodigoIn(Collection<String> codigos);
}
