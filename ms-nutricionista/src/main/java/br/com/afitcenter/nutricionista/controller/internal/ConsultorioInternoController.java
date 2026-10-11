package br.com.afitcenter.nutricionista.controller.internal;

import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioInternoResponse;
import br.com.afitcenter.nutricionista.service.ConsultorioService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Usado pelo ms-consulta quando a réplica ainda não recebeu o evento do consultório. */
@Hidden
@RestController
@RequestMapping("/internal/consultorios")
@RequiredArgsConstructor
public class ConsultorioInternoController {

    private final ConsultorioService service;

    @GetMapping("/{id}")
    public ConsultorioInternoResponse buscar(@PathVariable UUID id) {
        return service.buscarInterno(id);
    }
}
