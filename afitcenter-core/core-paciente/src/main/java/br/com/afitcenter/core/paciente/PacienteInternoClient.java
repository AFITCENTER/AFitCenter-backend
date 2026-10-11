package br.com.afitcenter.core.paciente;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Consulta síncrona ao ms-paciente, usada só quando a réplica local ainda não tem o paciente. */
@FeignClient(name = "ms-paciente", contextId = "pacienteInternoClient", path = "/internal/pacientes")
public interface PacienteInternoClient {

    /** 404 quando o paciente não existe. Pacientes excluídos voltam com {@code excluido=true}. */
    @GetMapping("/{id}")
    PacienteRef buscar(@PathVariable("id") UUID id);
}
