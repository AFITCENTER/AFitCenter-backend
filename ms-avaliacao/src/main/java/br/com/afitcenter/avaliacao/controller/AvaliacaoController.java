package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.AvaliacaoResumoResponse;
import br.com.afitcenter.avaliacao.service.AvaliacaoService;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/avaliacoes")
@RequiredArgsConstructor
@Tag(name = "Avaliações", description = "Avaliação corporal (bioimpedância, antropometria) com os valores do laudo (requisito 3).")
public class AvaliacaoController {

    private final AvaliacaoService service;

    @GetMapping
    @Operation(summary = "Listar avaliações", description = "Mais recentes primeiro, sem os resultados do laudo.")
    public PaginaResponse<AvaliacaoResumoResponse> listar(
            @RequestParam(required = false) UUID pacienteId,
            @Parameter(description = "Início do período da medição (ISO 8601, ex.: 2026-10-01T00:00:00-03:00)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @Parameter(description = "Fim do período, exclusivo")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId, inicio, fim, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar avaliação", description = "Inclui os resultados do laudo.")
    public AvaliacaoResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Registrar avaliação",
            description = "PESO, ALTURA e IMC não entram nos resultados (o IMC é calculado). SEGMENTAR, IMPEDANCIA, "
                    + "PERIMETRO e DOBRA_CUTANEA exigem região; as demais não aceitam. LABORATORIAL vai em exames. "
                    + "consultaId, se informado, deve ser uma consulta do mesmo paciente.")
    public ResponseEntity<AvaliacaoResponse> criar(@Valid @RequestBody AvaliacaoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar avaliação", description = "Os resultados enviados substituem os anteriores.")
    public AvaliacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AvaliacaoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir avaliação", description = "Exclusão lógica; a avaliação sai também da evolução.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
