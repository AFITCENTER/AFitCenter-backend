package br.com.afitcenter.dieta.integracao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/** Condições ativas do paciente no ms-paciente, para os alertas de restrição da dieta. */
@FeignClient(name = "ms-paciente", contextId = "pacienteCondicaoClient", path = "/internal/pacientes")
public interface PacienteCondicaoClient {

    /** Ids do catálogo de condições do ms-paciente. */
    @GetMapping("/{id}/condicoes-ativas")
    List<Integer> condicoesAtivas(@PathVariable("id") UUID pacienteId);
}
