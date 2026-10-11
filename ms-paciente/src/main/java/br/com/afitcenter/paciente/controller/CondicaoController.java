package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.CondicaoRequest;
import br.com.afitcenter.paciente.model.dto.Dtos.CondicaoResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.TipoCondicaoResponse;
import br.com.afitcenter.paciente.service.CondicaoService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Catálogo de condições", description = "Condições do sistema e as cadastradas pelo nutricionista.")
public class CondicaoController {

    private final CondicaoService service;

    @GetMapping("/tipos-condicao")
    @Operation(summary = "Tipos de condição (alergia, intolerância, doença, restrição física...)")
    public List<TipoCondicaoResponse> tipos() {
        return service.tipos();
    }

    @GetMapping("/condicoes")
    @Operation(summary = "Listar condições do sistema e as próprias")
    public List<CondicaoResponse> listar(@RequestParam(required = false) Integer tipoId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), tipoId);
    }

    @PostMapping("/condicoes")
    @Operation(summary = "Cadastrar condição própria")
    public ResponseEntity<CondicaoResponse> criar(@Valid @RequestBody CondicaoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/condicoes/{id}")
    @Operation(summary = "Atualizar condição própria")
    public CondicaoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody CondicaoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/condicoes/{id}")
    @Operation(summary = "Excluir condição própria", description = "409 se algum paciente usa a condição.")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
