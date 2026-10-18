package br.com.afitcenter.core.assinatura;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Consulta síncrona ao ms-planos, só quando a réplica local ainda não tem a assinatura. */
@FeignClient(name = "ms-planos", contextId = "assinaturaInternoClient", path = "/internal/assinaturas")
public interface AssinaturaInternoClient {

    /** Nunca 404: o ms-planos cria a assinatura no plano padrão se o nutricionista ainda não tiver uma. */
    @GetMapping("/{nutricionistaId}")
    AssinaturaRef buscar(@PathVariable("nutricionistaId") UUID nutricionistaId);
}
