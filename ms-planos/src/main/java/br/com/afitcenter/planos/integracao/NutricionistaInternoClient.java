package br.com.afitcenter.planos.integracao;

import br.com.afitcenter.planos.model.dto.Dtos.NutricionistaInternoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Rota interna do ms-nutricionista com os dados do pagador (token interno adicionado pelo interceptor do core-web). */
@FeignClient(name = "ms-nutricionista", contextId = "nutricionistaInternoClient", path = "/internal/nutricionistas")
public interface NutricionistaInternoClient {

    @GetMapping("/{id}")
    NutricionistaInternoResponse buscar(@PathVariable("id") UUID id);
}
