package br.com.afitcenter.avaliacao.integracao;

import br.com.afitcenter.avaliacao.model.dto.Dtos.ConsultaInterna;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Consulta síncrona ao ms-consulta para validar o vínculo de avaliações e exames com a consulta. */
@FeignClient(name = "ms-consulta", contextId = "consultaInternoClient", path = "/internal/consultas")
public interface ConsultaInternoClient {

    /** 404 quando a consulta não existe ou foi excluída. */
    @GetMapping("/{id}")
    ConsultaInterna buscar(@PathVariable("id") UUID id);
}
