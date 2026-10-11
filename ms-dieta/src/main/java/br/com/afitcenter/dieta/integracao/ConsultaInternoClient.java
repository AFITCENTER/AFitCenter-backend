package br.com.afitcenter.dieta.integracao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Valida no ms-consulta o vínculo da dieta com a consulta. Não há réplica: a chamada é rara. */
@FeignClient(name = "ms-consulta", contextId = "consultaInternoClient", path = "/internal/consultas")
public interface ConsultaInternoClient {

    /** 404 quando a consulta não existe ou foi excluída. */
    @GetMapping("/{id}")
    ConsultaRef buscar(@PathVariable("id") UUID id);

    /** Formato de {@code GET /internal/consultas/{id}}. */
    record ConsultaRef(UUID id, UUID pacienteId, UUID nutricionistaId, OffsetDateTime dataHora, String status) {
    }
}
