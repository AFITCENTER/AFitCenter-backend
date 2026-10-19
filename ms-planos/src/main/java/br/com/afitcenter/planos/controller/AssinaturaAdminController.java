package br.com.afitcenter.planos.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaResponse;
import br.com.afitcenter.planos.model.enums.Enums.StatusAssinatura;
import br.com.afitcenter.planos.service.AssinaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/assinaturas")
@RequiredArgsConstructor
@Tag(name = "Assinaturas (administração)", description = "Acompanhamento das assinaturas. Exige o papel ADMIN.")
public class AssinaturaAdminController {

    private final AssinaturaService service;

    @GetMapping
    @Operation(summary = "Listar assinaturas", description = "Mais recentes primeiro, com filtros opcionais.")
    public PaginaResponse<AssinaturaResponse> listar(
            @RequestParam(required = false) StatusAssinatura status,
            @RequestParam(required = false) Integer planoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return service.listar(status, planoId, pagina, tamanho);
    }

    @GetMapping("/{nutricionistaId}")
    @Operation(summary = "Assinatura de um nutricionista")
    public AssinaturaResponse buscar(@PathVariable UUID nutricionistaId) {
        return service.doNutricionista(nutricionistaId);
    }
}
