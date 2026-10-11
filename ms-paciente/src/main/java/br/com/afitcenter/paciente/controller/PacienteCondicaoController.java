package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.AtualizarPacienteCondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteCondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteCondicaoResponse;
import br.com.afitcenter.paciente.service.PacienteCondicaoService;
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
@RequestMapping("/v1/pacientes/{pacienteId}/condicoes")
@RequiredArgsConstructor
@Tag(name = "Condições do paciente", description = "Restrições alimentares e físicas, doenças, alergias e intolerâncias.")
public class PacienteCondicaoController {

    private final PacienteCondicaoService service;

    @GetMapping
    @Operation(summary = "Listar condições do paciente")
    public List<PacienteCondicaoResponse> listar(@PathVariable UUID pacienteId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId);
    }

    @PostMapping
    @Operation(summary = "Registrar condição para o paciente")
    public ResponseEntity<PacienteCondicaoResponse> adicionar(@PathVariable UUID pacienteId,
                                                              @Valid @RequestBody PacienteCondicaoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.adicionar(UsuarioAutenticado.nutricionistaId(), pacienteId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar gravidade, data de início, situação ou observação")
    public PacienteCondicaoResponse atualizar(@PathVariable UUID pacienteId, @PathVariable UUID id,
                                              @Valid @RequestBody AtualizarPacienteCondicaoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), pacienteId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover registro (para histórico, prefira marcar como inativa)")
    public ResponseEntity<Void> remover(@PathVariable UUID pacienteId, @PathVariable UUID id) {
        service.remover(UsuarioAutenticado.nutricionistaId(), pacienteId, id);
        return ResponseEntity.noContent().build();
    }
}
