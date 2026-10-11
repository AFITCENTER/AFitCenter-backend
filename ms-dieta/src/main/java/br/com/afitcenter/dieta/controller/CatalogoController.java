package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.dieta.model.dto.Dtos.GrupoResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.NutrienteResponse;
import br.com.afitcenter.dieta.repository.GrupoAlimentarRepository;
import br.com.afitcenter.dieta.repository.NutrienteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos do sistema, iguais para todos os nutricionistas. */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Grupos alimentares (TACO) e micronutrientes.")
public class CatalogoController {

    private final GrupoAlimentarRepository grupoRepository;
    private final NutrienteRepository nutrienteRepository;

    @GetMapping("/grupos-alimentares")
    @Transactional(readOnly = true)
    @Operation(summary = "Grupos alimentares")
    public List<GrupoResponse> grupos() {
        return grupoRepository.findAllByOrderByNome().stream().map(GrupoResponse::de).toList();
    }

    @GetMapping("/nutrientes")
    @Transactional(readOnly = true)
    @Operation(summary = "Micronutrientes", description = "Ids usados em `nutrientes` ao cadastrar um alimento próprio.")
    public List<NutrienteResponse> nutrientes() {
        return nutrienteRepository.findAllByOrderById().stream().map(NutrienteResponse::de).toList();
    }
}
