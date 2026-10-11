package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.MedicamentoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.MedicamentoResponse;
import br.com.afitcenter.paciente.service.MedicamentoService;
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
@RequestMapping("/v1/pacientes/{pacienteId}/medicamentos")
@RequiredArgsConstructor
@Tag(name = "Medicamentos e suplementos", description = "O que o paciente usa (interações droga-nutriente).")
public class MedicamentoController {

    private final MedicamentoService service;

    @GetMapping
    @Operation(summary = "Listar medicamentos e suplementos do paciente")
    public List<MedicamentoResponse> listar(@PathVariable UUID pacienteId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId);
    }

    @PostMapping
    @Operation(summary = "Registrar medicamento ou suplemento")
    public ResponseEntity<MedicamentoResponse> adicionar(@PathVariable UUID pacienteId,
                                                         @Valid @RequestBody MedicamentoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.adicionar(UsuarioAutenticado.nutricionistaId(), pacienteId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar medicamento ou suplemento")
    public MedicamentoResponse atualizar(@PathVariable UUID pacienteId, @PathVariable UUID id,
                                         @Valid @RequestBody MedicamentoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), pacienteId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover medicamento ou suplemento")
    public ResponseEntity<Void> remover(@PathVariable UUID pacienteId, @PathVariable UUID id) {
        service.remover(UsuarioAutenticado.nutricionistaId(), pacienteId, id);
        return ResponseEntity.noContent().build();
    }
}
