package br.com.afitcenter.consulta.controller;

import br.com.afitcenter.consulta.model.dto.Dtos.AnaliseRequest;
import br.com.afitcenter.consulta.model.dto.Dtos.AnaliseResponse;
import br.com.afitcenter.consulta.service.AnaliseService;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/consultas/{consultaId}/analises")
@RequiredArgsConstructor
@Tag(name = "Análises", description = "Anamnese, queixas, achados, diagnóstico nutricional e conduta (requisito 2).")
public class AnaliseController {

    private final AnaliseService service;

    @GetMapping
    @Operation(summary = "Listar análises da consulta")
    public List<AnaliseResponse> listar(@PathVariable UUID consultaId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), consultaId);
    }

    @PostMapping
    @Operation(summary = "Registrar análise")
    public ResponseEntity<AnaliseResponse> registrar(@PathVariable UUID consultaId, @Valid @RequestBody AnaliseRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.registrar(UsuarioAutenticado.nutricionistaId(), consultaId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar análise")
    public AnaliseResponse atualizar(@PathVariable UUID consultaId, @PathVariable UUID id,
                                     @Valid @RequestBody AnaliseRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), consultaId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir análise (exclusão lógica)")
    public ResponseEntity<Void> excluir(@PathVariable UUID consultaId, @PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), consultaId, id);
        return ResponseEntity.noContent().build();
    }
}
