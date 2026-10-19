package br.com.afitcenter.planos.controller.internal;

import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaInternoResponse;
import br.com.afitcenter.planos.service.AssinaturaService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Usado pelo core-assinatura (ms-paciente, ms-nutricionista) quando a réplica ainda não tem a assinatura. */
@Hidden
@RestController
@RequestMapping("/internal/assinaturas")
@RequiredArgsConstructor
public class AssinaturaInternoController {

    private final AssinaturaService service;

    @GetMapping("/{nutricionistaId}")
    public AssinaturaInternoResponse buscar(@PathVariable UUID nutricionistaId) {
        return service.interno(nutricionistaId);
    }
}
