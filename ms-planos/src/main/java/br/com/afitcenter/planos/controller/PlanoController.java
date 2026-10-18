package br.com.afitcenter.planos.controller;

import br.com.afitcenter.planos.model.dto.Dtos.PlanoResponse;
import br.com.afitcenter.planos.service.PlanoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/planos")
@RequiredArgsConstructor
@Tag(name = "Planos", description = "Catálogo público de planos (página de preços). Não exige login.")
public class PlanoController {

    private final PlanoService service;

    @GetMapping
    @Operation(summary = "Planos à venda", description = "Ativos e públicos, na ordem de exibição. Limite nulo = ilimitado.")
    public List<PlanoResponse> catalogo() {
        return service.catalogo();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar plano à venda")
    public PlanoResponse buscar(@PathVariable Integer id) {
        return service.buscarPublico(id);
    }
}
