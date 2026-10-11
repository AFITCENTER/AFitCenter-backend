package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.MedidaRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.MedidaResponse;
import br.com.afitcenter.dieta.service.AlimentoMedidaService;
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
@RequestMapping("/v1/alimentos/{alimentoId}/medidas")
@RequiredArgsConstructor
@Tag(name = "Medidas caseiras", description = "\"1 colher de sopa cheia\" = 25 g. Alteráveis só nos alimentos próprios.")
public class AlimentoMedidaController {

    private final AlimentoMedidaService service;

    @GetMapping
    @Operation(summary = "Listar medidas do alimento")
    public List<MedidaResponse> listar(@PathVariable Integer alimentoId) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), alimentoId);
    }

    @PostMapping
    @Operation(summary = "Cadastrar medida", description = "422 na base oficial; 409 se a descrição já existir no alimento.")
    public ResponseEntity<MedidaResponse> criar(@PathVariable Integer alimentoId, @Valid @RequestBody MedidaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(UsuarioAutenticado.nutricionistaId(), alimentoId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar medida", description = "Itens de dieta já cadastrados mantêm as gramas calculadas na inclusão.")
    public MedidaResponse atualizar(@PathVariable Integer alimentoId, @PathVariable Integer id,
                                    @Valid @RequestBody MedidaRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), alimentoId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir medida", description = "409 se a medida estiver em uso em alguma dieta.")
    public ResponseEntity<Void> excluir(@PathVariable Integer alimentoId, @PathVariable Integer id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), alimentoId, id);
        return ResponseEntity.noContent().build();
    }
}
