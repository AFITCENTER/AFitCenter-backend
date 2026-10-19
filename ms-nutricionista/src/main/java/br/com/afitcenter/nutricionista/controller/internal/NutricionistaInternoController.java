package br.com.afitcenter.nutricionista.controller.internal;

import br.com.afitcenter.nutricionista.model.dto.Dtos.NutricionistaInternoResponse;
import br.com.afitcenter.nutricionista.service.PerfilService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Usado pelo ms-planos para informar o pagador nas cobranças da assinatura. O Gateway bloqueia estas rotas. */
@Hidden
@RestController
@RequestMapping("/internal/nutricionistas")
@RequiredArgsConstructor
public class NutricionistaInternoController {

    private final PerfilService service;

    @GetMapping("/{id}")
    public NutricionistaInternoResponse buscar(@PathVariable UUID id) {
        return service.buscarInterno(id);
    }
}
