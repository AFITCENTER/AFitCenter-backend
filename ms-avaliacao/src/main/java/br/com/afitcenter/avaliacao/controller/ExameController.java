package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.dto.Dtos.AnexoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AnexoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.ExameResumoResponse;
import br.com.afitcenter.avaliacao.service.ExameService;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/exames")
@RequiredArgsConstructor
@Tag(name = "Exames", description = "Exames laboratoriais com resultados e anexos (requisito 6).")
public class ExameController {

    private final ExameService service;

    @GetMapping
    @Operation(summary = "Listar exames", description = "Coletas mais recentes primeiro, sem resultados e anexos.")
    public PaginaResponse<ExameResumoResponse> listar(
            @RequestParam(required = false) UUID pacienteId,
            @Parameter(description = "Data de coleta inicial (inclusive), ex.: 2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de coleta final (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId, inicio, fim, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar exame", description = "Inclui resultados e anexos.")
    public ExameResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Registrar exame",
            description = "Só métricas LABORATORIAL, uma vez cada. consultaId, se informado, deve ser do mesmo paciente.")
    public ResponseEntity<ExameResponse> criar(@Valid @RequestBody ExameRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar exame", description = "Os resultados enviados substituem os anteriores.")
    public ExameResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ExameRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir exame", description = "Exclusão lógica; o exame sai também da evolução.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/anexos")
    @Operation(summary = "Registrar anexo",
            description = "Só os metadados de um arquivo já enviado ao storage (o upload ainda não existe).")
    public ResponseEntity<AnexoResponse> anexar(@PathVariable UUID id, @Valid @RequestBody AnexoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.anexar(UsuarioAutenticado.nutricionistaId(), id, dados));
    }

    @DeleteMapping("/{id}/anexos/{anexoId}")
    @Operation(summary = "Remover anexo", description = "Remove os metadados.")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        service.removerAnexo(UsuarioAutenticado.nutricionistaId(), id, anexoId);
        return ResponseEntity.noContent().build();
    }
}
