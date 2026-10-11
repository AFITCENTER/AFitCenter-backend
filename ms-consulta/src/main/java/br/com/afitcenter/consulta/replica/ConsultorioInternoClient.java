package br.com.afitcenter.consulta.replica;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Consulta síncrona ao ms-nutricionista, só quando a réplica ainda não tem o consultório. */
@FeignClient(name = "ms-nutricionista", contextId = "consultorioInternoClient", path = "/internal/consultorios")
public interface ConsultorioInternoClient {

    @GetMapping("/{id}")
    ConsultorioRef buscar(@PathVariable("id") UUID id);
}
