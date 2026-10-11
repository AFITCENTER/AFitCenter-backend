package br.com.afitcenter.consulta.controller.internal;

import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaInternoResponse;
import br.com.afitcenter.consulta.service.ConsultaService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Usado pelo ms-avaliacao e ms-dieta para validar o vínculo de avaliações e dietas com a consulta. */
@Hidden
@RestController
@RequestMapping("/internal/consultas")
@RequiredArgsConstructor
public class ConsultaInternoController {

    private final ConsultaService service;

    @GetMapping("/{id}")
    public ConsultaInternoResponse buscar(@PathVariable UUID id) {
        return service.buscarInterno(id);
    }
}
