package br.com.afitcenter.planos.controller;

import br.com.afitcenter.planos.model.dto.Dtos.AtivoRequest;
import br.com.afitcenter.planos.model.dto.Dtos.PlanoRequest;
import br.com.afitcenter.planos.model.dto.Dtos.PlanoResponse;
import br.com.afitcenter.planos.service.PlanoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/planos")
@RequiredArgsConstructor
@Tag(name = "Planos (administração)", description = "Manutenção do catálogo. Exige o papel ADMIN.")
public class PlanoAdminController {

    private final PlanoService service;

    @GetMapping
    @Operation(summary = "Todos os planos", description = "Inclui inativos e não públicos.")
    public List<PlanoResponse> listar() {
        return service.listarTodos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar plano")
    public PlanoResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @PostMapping
    @Operation(summary = "Criar plano", description = "409 se o código já existir.")
    public ResponseEntity<PlanoResponse> criar(@Valid @RequestBody PlanoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar plano",
            description = "O código não muda. Novo preço vale para as próximas faturas; novos limites valem na hora "
                    + "para todas as assinaturas do plano.")
    public PlanoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody PlanoRequest dados) {
        return service.atualizar(id, dados);
    }

    @PatchMapping("/{id}/ativo")
    @Operation(summary = "Ativar ou desativar", description = "Desativado não aceita novas assinaturas; as atuais renovam.")
    public PlanoResponse alterarAtivo(@PathVariable Integer id, @Valid @RequestBody AtivoRequest dados) {
        return service.alterarAtivo(id, dados.ativo());
    }
}
