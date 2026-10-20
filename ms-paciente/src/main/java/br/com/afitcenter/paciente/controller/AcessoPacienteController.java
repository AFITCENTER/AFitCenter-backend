package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.CodigoAcessoResponse;
import br.com.afitcenter.paciente.service.AcessoPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/pacientes/{pacienteId}/acesso")
@RequiredArgsConstructor
@Tag(name = "Acesso do paciente", description = "Código que liga a ficha à conta do paciente no portal.")
public class AcessoPacienteController {

    private final AcessoPortalService service;

    @PostMapping
    @Operation(summary = "Gerar código de acesso",
            description = "Entregue o código ao paciente: depois de criar a conta no ms-pessoa e entrar, ele informa o "
                    + "código no portal e passa a ver esta ficha. Uso único, com validade; gerar outro invalida o "
                    + "anterior. 409 se a ficha já está ligada a uma conta.")
    public ResponseEntity<CodigoAcessoResponse> gerarCodigo(@PathVariable UUID pacienteId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.gerarCodigo(UsuarioAutenticado.nutricionistaId(), pacienteId));
    }

    @DeleteMapping
    @Operation(summary = "Desfazer o acesso", description = "A conta ligada deixa de ver a ficha e o código pendente deixa de valer.")
    public ResponseEntity<Void> revogar(@PathVariable UUID pacienteId) {
        service.revogar(UsuarioAutenticado.nutricionistaId(), pacienteId);
        return ResponseEntity.noContent().build();
    }
}
