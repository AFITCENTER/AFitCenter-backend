package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.PacienteResumoResponse;
import br.com.afitcenter.paciente.model.enums.Enums.StatusPaciente;
import br.com.afitcenter.paciente.service.PacienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/pacientes")
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Fichas dos pacientes do nutricionista logado (requisito 1).")
public class PacienteController {

    private final PacienteService service;

    @GetMapping
    @Operation(summary = "Listar pacientes", description = "Ordenado por nome. Busca por parte do nome, nome social ou CPF.")
    public PaginaResponse<PacienteResumoResponse> listar(
            @Parameter(description = "Parte do nome/nome social ou CPF completo") @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusPaciente status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), busca, status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar paciente")
    public PacienteResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar paciente")
    public ResponseEntity<PacienteResponse> criar(@Valid @RequestBody PacienteRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar paciente", description = "Substitui os dados, inclusive a lista de contatos.")
    public PacienteResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PacienteRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir paciente", description = "Exclusão lógica: o prontuário é mantido.")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
