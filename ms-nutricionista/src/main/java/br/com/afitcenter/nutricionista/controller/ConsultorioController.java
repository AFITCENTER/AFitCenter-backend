package br.com.afitcenter.nutricionista.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.ConsultorioResponse;
import br.com.afitcenter.nutricionista.service.ConsultorioService;
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
@RequestMapping("/v1/consultorios")
@RequiredArgsConstructor
@Tag(name = "Consultórios", description = "Locais de atendimento do nutricionista logado.")
public class ConsultorioController {

    private final ConsultorioService service;

    @GetMapping
    @Operation(summary = "Listar meus consultórios ativos")
    public List<ConsultorioResponse> listar() {
        return service.listar(UsuarioAutenticado.nutricionistaId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar consultório")
    public ConsultorioResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar consultório")
    public ResponseEntity<ConsultorioResponse> criar(@Valid @RequestBody ConsultorioRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar consultório")
    public ConsultorioResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ConsultorioRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar consultório", description = "Exclusão lógica; consultas antigas são mantidas.")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        service.desativar(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
