package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.GrupoAlimentar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrupoAlimentarRepository extends JpaRepository<GrupoAlimentar, Integer> {

    List<GrupoAlimentar> findAllByOrderByNome();
}
