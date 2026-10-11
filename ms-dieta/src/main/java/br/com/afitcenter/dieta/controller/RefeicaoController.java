package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.RefeicaoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.RefeicaoResponse;
import br.com.afitcenter.dieta.service.RefeicaoService;
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
@RequestMapping("/v1/dietas/{dietaId}/refeicoes")
@RequiredArgsConstructor
@Tag(name = "Refeições", description = "Refeições da dieta, com horário e dias da semana (1 = segunda ... 7 = domingo).")
public class RefeicaoController {

    private final RefeicaoService service;

    @GetMapping
    @Operation(summary = "Listar refeições", description = "Em ordem, com itens e substitutos.")
    public List<RefeicaoResponse> listar(@PathVariable UUID dietaId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), dietaId);
    }

    @PostMapping
    @Operation(summary = "Criar refeição", description = "Sem `diasSemana`, vale para todos os dias; sem `ordem`, entra no fim.")
    public ResponseEntity<RefeicaoResponse> criar(@PathVariable UUID dietaId, @Valid @RequestBody RefeicaoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(UsuarioAutenticado.nutricionistaId(), dietaId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar refeição")
    public RefeicaoResponse atualizar(@PathVariable UUID dietaId, @PathVariable UUID id,
                                      @Valid @RequestBody RefeicaoRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), dietaId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir refeição", description = "Remove também os itens e substitutos.")
    public ResponseEntity<Void> excluir(@PathVariable UUID dietaId, @PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), dietaId, id);
        return ResponseEntity.noContent().build();
    }
}
