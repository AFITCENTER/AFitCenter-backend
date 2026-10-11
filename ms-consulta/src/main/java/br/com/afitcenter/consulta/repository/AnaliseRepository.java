package br.com.afitcenter.consulta.repository;

import br.com.afitcenter.consulta.model.entity.Analise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnaliseRepository extends JpaRepository<Analise, UUID> {

    List<Analise> findAllByConsultaIdAndDeletadoEmIsNullOrderByCriadoEm(UUID consultaId);

    Optional<Analise> findByIdAndConsultaIdAndDeletadoEmIsNull(UUID id, UUID consultaId);
}
