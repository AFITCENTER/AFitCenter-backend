package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.TipoCondicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoCondicaoRepository extends JpaRepository<TipoCondicao, Integer> {

    List<TipoCondicao> findAllByOrderByNome();
}
