package br.com.afitcenter.nutricionista.repository;

import br.com.afitcenter.nutricionista.model.entity.Consultorio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sempre filtre por {@code nutricionistaId}: os métodos herdados ({@code findById}...) não isolam o tenant. */
public interface ConsultorioRepository extends JpaRepository<Consultorio, UUID> {

    List<Consultorio> findAllByNutricionistaIdAndAtivoTrueOrderByNome(UUID nutricionistaId);

    Optional<Consultorio> findByIdAndNutricionistaIdAndAtivoTrue(UUID id, UUID nutricionistaId);
}
