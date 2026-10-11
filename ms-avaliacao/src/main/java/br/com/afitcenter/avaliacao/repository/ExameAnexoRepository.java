package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.ExameAnexo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** O exame (e com ele o nutricionista) é conferido antes de chegar aqui. */
public interface ExameAnexoRepository extends JpaRepository<ExameAnexo, UUID> {

    List<ExameAnexo> findAllByExameIdOrderByCriadoEm(UUID exameId);

    Optional<ExameAnexo> findByIdAndExameId(UUID id, UUID exameId);
}
