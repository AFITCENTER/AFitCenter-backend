package br.com.afitcenter.dieta.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.dieta.model.dto.Dtos.ItemRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.ItemResponse;
import br.com.afitcenter.dieta.model.dto.Dtos.SubstitutoRequest;
import br.com.afitcenter.dieta.model.dto.Dtos.SubstitutoResponse;
import br.com.afitcenter.dieta.service.RefeicaoItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/dietas/{dietaId}/refeicoes/{refeicaoId}/itens")
@RequiredArgsConstructor
@Tag(name = "Itens da refeição", description = "Alimentos da refeição, em medida caseira ou gramas, e seus substitutos.")
public class RefeicaoItemController {

    private final RefeicaoItemService service;

    @PostMapping
    @Operation(summary = "Adicionar item",
            description = "`quantidadeG` = gramas da medida × quantidade (ou a própria quantidade, sem medida). "
                    + "A medida precisa ser do mesmo alimento; o alimento, da base oficial ou próprio.")
    public ResponseEntity<ItemResponse> adicionar(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId,
                                                  @Valid @RequestBody ItemRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.adicionarItem(UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar item")
    public ItemResponse atualizar(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId, @PathVariable UUID id,
                                  @Valid @RequestBody ItemRequest dados) {
        return service.atualizarItem(UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir item", description = "Remove também os substitutos.")
    public ResponseEntity<Void> excluir(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId, @PathVariable UUID id) {
        service.excluirItem(UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{itemId}/substitutos")
    @Operation(summary = "Adicionar substituto", description = "Mesmas regras de alimento e medida do item.")
    public ResponseEntity<SubstitutoResponse> adicionarSubstituto(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId,
                                                                  @PathVariable UUID itemId,
                                                                  @Valid @RequestBody SubstitutoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarSubstituto(
                UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, itemId, dados));
    }

    @PutMapping("/{itemId}/substitutos/{id}")
    @Operation(summary = "Atualizar substituto")
    public SubstitutoResponse atualizarSubstituto(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId,
                                                  @PathVariable UUID itemId, @PathVariable UUID id,
                                                  @Valid @RequestBody SubstitutoRequest dados) {
        return service.atualizarSubstituto(UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, itemId, id, dados);
    }

    @DeleteMapping("/{itemId}/substitutos/{id}")
    @Operation(summary = "Excluir substituto")
    public ResponseEntity<Void> excluirSubstituto(@PathVariable UUID dietaId, @PathVariable UUID refeicaoId,
                                                  @PathVariable UUID itemId, @PathVariable UUID id) {
        service.excluirSubstituto(UsuarioAutenticado.nutricionistaId(), dietaId, refeicaoId, itemId, id);
        return ResponseEntity.noContent().build();
    }
}
