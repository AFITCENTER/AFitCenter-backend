package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.dto.Dtos.EquipamentoRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.EquipamentoResponse;
import br.com.afitcenter.avaliacao.service.EquipamentoService;
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

@RestController
@RequestMapping("/v1/equipamentos")
@RequiredArgsConstructor
@Tag(name = "Equipamentos", description = "Catálogo do sistema mais os equipamentos cadastrados pelo nutricionista.")
public class EquipamentoController {

    private final EquipamentoService service;

    @GetMapping
    @Operation(summary = "Listar equipamentos", description = "Do sistema (doSistema=true) e os próprios.")
    public List<EquipamentoResponse> listar() {
        return service.listar(UsuarioAutenticado.nutricionistaId());
    }

    @PostMapping
    @Operation(summary = "Cadastrar equipamento próprio", description = "409 se já existir a mesma marca e modelo.")
    public ResponseEntity<EquipamentoResponse> criar(@Valid @RequestBody EquipamentoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(UsuarioAutenticado.nutricionistaId(), dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar equipamento próprio", description = "Os do sistema respondem 422.")
    public EquipamentoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody EquipamentoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir equipamento próprio", description = "409 se estiver registrado em alguma avaliação.")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
