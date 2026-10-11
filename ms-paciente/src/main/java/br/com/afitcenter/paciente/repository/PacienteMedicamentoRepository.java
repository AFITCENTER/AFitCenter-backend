package br.com.afitcenter.paciente.repository;

import br.com.afitcenter.paciente.model.entity.PacienteMedicamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PacienteMedicamentoRepository extends JpaRepository<PacienteMedicamento, UUID> {

    List<PacienteMedicamento> findAllByPacienteIdOrderByNome(UUID pacienteId);

    Optional<PacienteMedicamento> findByIdAndPacienteId(UUID id, UUID pacienteId);
}
