package br.com.afitcenter.paciente.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.paciente.model.dto.Dtos.FichaPortalResponse;
import br.com.afitcenter.paciente.model.dto.Dtos.VinculoRequest;
import br.com.afitcenter.paciente.service.AcessoPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Rotas do paciente logado (papel PACIENTE). Isolamento por conta: cada um só vê as fichas ligadas a ele. */
@RestController
@RequestMapping("/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal do paciente", description = "Fichas ligadas à conta do paciente logado.")
public class PortalController {

    private final AcessoPortalService service;

    @GetMapping("/fichas")
    @Operation(summary = "Minhas fichas", description = "Uma por nutricionista que liberou o acesso.")
    public List<FichaPortalResponse> fichas() {
        return service.fichas(UsuarioAutenticado.usuarioId());
    }

    @PostMapping("/vinculos")
    @Operation(summary = "Ligar uma ficha à minha conta", description = "Com o código de acesso entregue pelo nutricionista.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Ficha ligada"),
                    @ApiResponse(responseCode = "409", description = "Conta já ligada a uma ficha deste nutricionista"),
                    @ApiResponse(responseCode = "422", description = "Código inválido ou vencido"),
                    @ApiResponse(responseCode = "429", description = "Muitos códigos errados")
            })
    public ResponseEntity<FichaPortalResponse> vincular(@Valid @RequestBody VinculoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.vincular(UsuarioAutenticado.usuarioId(), dados.codigo()));
    }
}
