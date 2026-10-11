package br.com.afitcenter.avaliacao.repository;

import br.com.afitcenter.avaliacao.model.entity.Meta;
import br.com.afitcenter.avaliacao.model.enums.Enums.StatusMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetaRepository extends JpaRepository<Meta, UUID> {

    Optional<Meta> findByIdAndPacienteIdAndNutricionistaId(UUID id, UUID pacienteId, UUID nutricionistaId);

    List<Meta> findAllByNutricionistaIdAndPacienteIdOrderByDataInicioDescCriadoEmDesc(UUID nutricionistaId,
                                                                                     UUID pacienteId);

    List<Meta> findAllByNutricionistaIdAndPacienteIdAndStatusOrderByDataInicioDescCriadoEmDesc(
            UUID nutricionistaId, UUID pacienteId, StatusMeta status);

    @Query("""
            SELECT m FROM Meta m
            WHERE m.nutricionistaId = :nutricionistaId AND m.pacienteId = :pacienteId
              AND m.status = :status AND m.metrica.codigo IN :metricas
            ORDER BY m.metrica.id, m.prazo NULLS LAST
            """)
    List<Meta> porMetricas(@Param("nutricionistaId") UUID nutricionistaId, @Param("pacienteId") UUID pacienteId,
                           @Param("status") StatusMeta status, @Param("metricas") Collection<String> metricas);
}
