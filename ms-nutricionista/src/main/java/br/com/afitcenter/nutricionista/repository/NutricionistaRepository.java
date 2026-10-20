package br.com.afitcenter.nutricionista.repository;

import br.com.afitcenter.nutricionista.model.entity.Nutricionista;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NutricionistaRepository extends JpaRepository<Nutricionista, UUID> {

    boolean existsByCrnRegiaoAndCrnNumero(Integer crnRegiao, String crnNumero);
}
