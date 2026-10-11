package br.com.afitcenter.avaliacao.controller;

import br.com.afitcenter.avaliacao.model.dto.Dtos.MetaRequest;
import br.com.afitcenter.avaliacao.model.dto.Dtos.MetaResponse;
import br.com.afitcenter.avaliacao.model.enums.Enums.StatusMeta;
import br.com.afitcenter.avaliacao.service.MetaService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/pacientes/{pacienteId}/metas")
@RequiredArgsConstructor
@Tag(name = "Metas", description = "Valores-alvo do paciente (peso, % de gordura, glicemia...).")
public class MetaController {

    private final MetaService service;

    @GetMapping
    @Operation(summary = "Listar metas do paciente")
    public List<MetaResponse> listar(@PathVariable UUID pacienteId, @RequestParam(required = false) StatusMeta status) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pacienteId, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar meta")
    public MetaResponse buscar(@PathVariable UUID pacienteId, @PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), pacienteId, id);
    }

    @PostMapping
    @Operation(summary = "Criar meta",
            description = "Só métricas do corpo inteiro. dataInicio padrão: hoje. Status inicial: ATIVA.")
    public ResponseEntity<MetaResponse> criar(@PathVariable UUID pacienteId, @Valid @RequestBody MetaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(UsuarioAutenticado.nutricionistaId(), pacienteId, dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar meta", description = "Também marca como ATINGIDA ou CANCELADA.")
    public MetaResponse atualizar(@PathVariable UUID pacienteId, @PathVariable UUID id,
                                  @Valid @RequestBody MetaRequest dados) {
        return service.atualizar(UsuarioAutenticado.nutricionistaId(), pacienteId, id, dados);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir meta", description = "Exclusão física. Para manter o histórico, use o status CANCELADA.")
    public ResponseEntity<Void> excluir(@PathVariable UUID pacienteId, @PathVariable UUID id) {
        service.excluir(UsuarioAutenticado.nutricionistaId(), pacienteId, id);
        return ResponseEntity.noContent().build();
    }
}
