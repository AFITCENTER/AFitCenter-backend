package br.com.afitcenter.dieta.repository;

import br.com.afitcenter.dieta.model.entity.Nutriente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NutrienteRepository extends JpaRepository<Nutriente, Integer> {

    List<Nutriente> findAllByOrderById();
}
