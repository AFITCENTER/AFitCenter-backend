package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.AplicarModeloRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaDetalheResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.DietaResponse;
import br.com.afitcenter.dieta.service.DietaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Modelos são dietas com {@code modelo = true}: criados, editados e excluídos em /v1/dietas. */
@RestController
@RequestMapping("/v1/modelos")
@RequiredArgsConstructor
@Tag(name = "Modelos de dieta", description = "Dietas reutilizáveis, sem paciente, aplicadas por cópia.")
public class ModeloController {

    private final DietaService service;

    @GetMapping
    @Operation(summary = "Listar modelos", description = "Em ordem de título, com busca por parte do título.")
    public PaginaResponse<DietaResponse> listar(@RequestParam(required = false) String busca,
                                                @RequestParam(defaultValue = "0") int pagina,
                                                @RequestParam(defaultValue = "50") int tamanho) {
        return service.listarModelos(UsuarioAutenticado.nutricionistaId(), busca, pagina, tamanho);
    }

    @PostMapping("/{id}/aplicar")
    @Operation(summary = "Aplicar modelo ao paciente",
            description = "Cópia profunda (refeições, itens e substitutos) para uma nova dieta do paciente, em RASCUNHO, "
                    + "com `dietaOrigemId` = modelo. Alterar a cópia não muda o modelo.")
    public ResponseEntity<DietaDetalheResponse> aplicar(@PathVariable UUID id, @Valid @RequestBody AplicarModeloRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.aplicarModelo(UsuarioAutenticado.nutricionistaId(), id, dados));
    }
}
