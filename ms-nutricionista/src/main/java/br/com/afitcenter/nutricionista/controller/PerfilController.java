package br.com.afitcenter.nutricionista.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.nutricionista.model.dto.Dtos.AtualizarPerfilRequest;
import br.com.afitcenter.nutricionista.model.dto.Dtos.NutricionistaResponse;
import br.com.afitcenter.nutricionista.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/nutricionistas/me")
@RequiredArgsConstructor
@Tag(name = "Perfil", description = "Dados profissionais do nutricionista logado (requisito 7). Os pessoais ficam no "
        + "ms-pessoa (GET /v1/conta).")
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping
    @Operation(summary = "Meu perfil")
    public NutricionistaResponse buscar() {
        return perfilService.buscar(UsuarioAutenticado.nutricionistaId());
    }

    @PutMapping
    @Operation(summary = "Atualizar meu perfil", description = "Especialidades, assinatura, logo e endereço profissional. "
            + "Nome e telefone mudam no ms-pessoa; CRN não muda.")
    public NutricionistaResponse atualizar(@Valid @RequestBody AtualizarPerfilRequest dados) {
        return perfilService.atualizar(UsuarioAutenticado.nutricionistaId(), dados);
    }
}
