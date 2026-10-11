package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.dto.Dtos.ComparacaoResponse;
import br.com.afitcenter.avaliacao.model.dto.Dtos.EvolucaoResponse;
import br.com.afitcenter.avaliacao.service.EvolucaoService;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/pacientes/{pacienteId}")
@RequiredArgsConstructor
@Tag(name = "Evolução", description = "Séries para gráficos e comparação entre avaliações (requisito 3).")
public class EvolucaoController {

    private final EvolucaoService service;

    @GetMapping("/evolucao")
    @Operation(summary = "Evolução das métricas",
            description = "Uma série por métrica e região, com os pontos das avaliações (inclusive PESO, ALTURA e IMC) "
                    + "e dos exames em ordem cronológica, mais as metas ativas das métricas pedidas.")
    public EvolucaoResponse evolucao(
            @PathVariable UUID pacienteId,
            @Parameter(description = "Códigos das métricas, separados por vírgula (ex.: PESO,GORDURA_PCT)")
            @RequestParam List<String> metricas,
            @Parameter(description = "Código da região; vazio traz todas as regiões e o corpo inteiro")
            @RequestParam(required = false) String regiao,
            @Parameter(description = "Início do período (ISO 8601, ex.: 2026-01-01T00:00:00-03:00)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @Parameter(description = "Fim do período, exclusivo")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim) {
        return service.evolucao(UsuarioAutenticado.nutricionistaId(), pacienteId, metricas, regiao, inicio, fim);
    }

    @GetMapping("/comparacao")
    @Operation(summary = "Comparar duas avaliações",
            description = "Por métrica e região: valor antes, depois e a diferença (depois - antes). "
                    + "A ordem de a e b não importa: \"antes\" é a medição mais antiga.")
    public ComparacaoResponse comparar(@PathVariable UUID pacienteId, @RequestParam UUID a, @RequestParam UUID b) {
        return service.comparar(UsuarioAutenticado.nutricionistaId(), pacienteId, a, b);
    }
}
