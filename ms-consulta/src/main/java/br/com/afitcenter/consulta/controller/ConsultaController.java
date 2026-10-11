package br.com.afitcenter.consulta.controller;

import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaRequest;
import br.com.afitcenter.consulta.model.dto.Dtos.ConsultaResponse;
import br.com.afitcenter.consulta.model.dto.Dtos.StatusRequest;
import br.com.afitcenter.consulta.model.enums.Enums.StatusConsulta;
import br.com.afitcenter.consulta.service.ConsultaService;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/v1/consultas")
@RequiredArgsConstructor
@Tag(name = "Consultas", description = "Agenda e consultas do nutricionista logado (requisito 4).")
public class ConsultaController {

    private final ConsultaService service;

    @GetMapping
    @Operation(summary = "Agenda", description = "Consultas em ordem de data/hora, com filtros opcionais.")
    public PaginaResponse<ConsultaResponse> agenda(
            @Parameter(description = "Início do período (ISO 8601, ex.: 2026-10-01T00:00:00-03:00)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @Parameter(description = "Fim do período, exclusivo")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(required = false) UUID pacienteId,
            @RequestParam(required = false) StatusConsulta status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return service.agenda(UsuarioAutenticado.nutricionistaId(), inicio, fim, pacienteId, status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar consulta")
    public ConsultaResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Agendar consulta", description = "409 se o horário conflitar com outra consulta.")
    public ResponseEntity<ConsultaResponse> criar(@Valid @RequestBody ConsultaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar ou reagendar consulta")
    public ConsultaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ConsultaRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mudar status",
            description = "AGENDADA → CONFIRMADA/REALIZADA/CANCELADA/FALTOU; CONFIRMADA → REALIZADA/CANCELADA/FALTOU.")
    public ConsultaResponse alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest dados) {
        return service.alterarStatus(UsuarioAutenticado.nutricionistaId(), id, dados.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir consulta", description = "Exclusão lógica. Para desmarcar, prefira o status CANCELADA.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
