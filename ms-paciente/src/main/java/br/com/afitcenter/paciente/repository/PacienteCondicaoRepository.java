package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.PacienteCondicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PacienteCondicaoRepository extends JpaRepository<PacienteCondicao, UUID> {

    List<PacienteCondicao> findAllByPacienteIdOrderByCriadoEm(UUID pacienteId);

    Optional<PacienteCondicao> findByIdAndPacienteId(UUID id, UUID pacienteId);

    boolean existsByPacienteIdAndCondicaoId(UUID pacienteId, Integer condicaoId);

    @Query("SELECT pc.condicao.id FROM PacienteCondicao pc WHERE pc.pacienteId = :pacienteId AND pc.ativa = true")
    List<Integer> findCondicaoIdsAtivas(@Param("pacienteId") UUID pacienteId);
}
